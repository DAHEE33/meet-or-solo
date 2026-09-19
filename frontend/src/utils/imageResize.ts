/**
 * 프로필 이미지를 업로드 전에 줄인다.
 *
 * 왜 필요한가
 *   아이폰 사진은 HEIC로 저장되는데, 웹 업로드에 HEIC를 쓸 수 없어 iOS가 JPEG로 변환해서
 *   넘긴다. HEIC가 JPEG보다 2~3배 효율이 좋아서, 갤러리에 3MB로 보이던 사진이 브라우저에는
 *   8~10MB로 도착한다. 그래서 평범하게 찍은 사진이 5MB 상한에 걸려 거부됐다.
 *
 *   프로필 사진은 화면에서 가장 크게 보이는 곳이 96px(프로필 수정 미리보기)이고 나머지는
 *   48~56px이다. 원본 해상도를 올릴 이유가 없다. 512px로 줄이면 3배 화면에도 넉넉하고
 *   파일은 50KB 수준으로 떨어진다. 상한 문제가 원천적으로 사라진다.
 */

/** 긴 변 최대 길이. 화면 최대 표시가 96px이라 3배 화면(288px)에도 여유가 있다. */
export const MAX_DIMENSION = 512;

/** JPEG 품질. 0.85면 512px에서 눈에 띄는 열화가 없다. */
export const OUTPUT_QUALITY = 0.85;

/**
 * 이미 충분히 작은 파일은 다시 인코딩하지 않는다.
 *
 * 작은 PNG를 굳이 JPEG로 바꾸면 화질만 잃고 크기는 줄지 않을 수 있다.
 * 투명 배경도 사라진다.
 */
export const PASS_THROUGH_MAX_BYTES = 1024 * 1024;

/** 줄인 뒤에도 이 값을 넘으면 거부한다. 실제로는 거의 발생하지 않는다. */
export const MAX_OUTPUT_BYTES = 5 * 1024 * 1024;

export const SUPPORTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;

export type ResizeFailureReason =
  /** JPEG, PNG, WEBP가 아니다. */
  | 'UNSUPPORTED_TYPE'
  /** 이미지를 읽지 못했다. 파일이 손상됐거나 브라우저가 지원하지 않는다. */
  | 'DECODE_FAILED'
  /** 줄였는데도 상한을 넘는다. */
  | 'TOO_LARGE';

export type ResizeResult =
  | { ok: true; file: File }
  | { ok: false; reason: ResizeFailureReason };

export function isSupportedImageType(type: string): boolean {
  return (SUPPORTED_TYPES as readonly string[]).includes(type);
}

/**
 * 긴 변이 max를 넘을 때만 비율을 유지하며 줄인다.
 *
 * 작은 이미지를 키우지 않는다. 키워봐야 화질은 그대로이고 파일만 커진다.
 */
export function calculateTargetSize(
  width: number,
  height: number,
  max: number = MAX_DIMENSION,
): { width: number; height: number } {
  const longest = Math.max(width, height);
  if (longest <= max) return { width, height };
  const ratio = max / longest;
  // 반올림 결과가 0이 되지 않게 한다. 1px짜리 canvas는 그리기에서 실패한다.
  return {
    width: Math.max(1, Math.round(width * ratio)),
    height: Math.max(1, Math.round(height * ratio)),
  };
}

/**
 * EXIF 방향을 적용해 이미지를 읽는다.
 *
 * 이 처리를 빠뜨리면 **아이폰 세로 사진이 90도 누운 채로 저장된다.** 원본 JPEG는 픽셀을
 * 가로로 담고 "돌려서 보여달라"는 EXIF 방향 값을 따로 들고 있는데, canvas에 그냥 그리면
 * 그 값이 무시되기 때문이다.
 *
 * createImageBitmap의 imageOrientation 옵션이 이것을 처리한다. 지원하지 않는 브라우저에서는
 * <img> 로드로 되돌아간다. 최신 브라우저의 <img>는 EXIF 방향을 기본으로 적용한다.
 */
async function decode(file: File): Promise<CanvasImageSource & { width: number; height: number }> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      // 옵션을 모르는 브라우저는 여기로 떨어진다. 아래 <img> 경로를 쓴다.
    }
  }

  const url = URL.createObjectURL(file);
  try {
    return await new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => reject(new Error('이미지를 읽지 못했습니다.'));
      image.src = url;
    });
  } finally {
    URL.revokeObjectURL(url);
  }
}

function toBlob(canvas: HTMLCanvasElement): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', OUTPUT_QUALITY));
}

/** 확장자를 .jpg로 바꾼다. 내용이 JPEG인데 이름이 .png면 헷갈린다. */
function toJpegName(name: string): string {
  const base = name.replace(/\.[^.]+$/, '');
  return `${base || 'profile'}.jpg`;
}

export async function resizeProfileImage(file: File): Promise<ResizeResult> {
  if (!isSupportedImageType(file.type)) {
    return { ok: false, reason: 'UNSUPPORTED_TYPE' };
  }

  let source: CanvasImageSource & { width: number; height: number };
  try {
    source = await decode(file);
  } catch {
    return { ok: false, reason: 'DECODE_FAILED' };
  }

  const target = calculateTargetSize(source.width, source.height);

  // 이미 작고 가벼우면 원본을 그대로 쓴다. 다시 인코딩해봐야 잃기만 한다.
  if (target.width === source.width && target.height === source.height
      && file.size <= PASS_THROUGH_MAX_BYTES) {
    return { ok: true, file };
  }

  const canvas = document.createElement('canvas');
  canvas.width = target.width;
  canvas.height = target.height;
  const context = canvas.getContext('2d');
  if (!context) return { ok: false, reason: 'DECODE_FAILED' };

  // JPEG는 투명을 담지 못한다. 흰색을 먼저 깔지 않으면 투명 PNG의 배경이 검게 나온다.
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, target.width, target.height);
  context.drawImage(source, 0, 0, target.width, target.height);

  const blob = await toBlob(canvas);
  if (!blob) return { ok: false, reason: 'DECODE_FAILED' };
  if (blob.size > MAX_OUTPUT_BYTES) return { ok: false, reason: 'TOO_LARGE' };

  return {
    ok: true,
    file: new File([blob], toJpegName(file.name), {
      type: 'image/jpeg',
      lastModified: Date.now(),
    }),
  };
}

/** 실패 사유를 화면에 보여줄 문구로 바꾼다. */
export function resizeFailureMessage(reason: ResizeFailureReason): string {
  switch (reason) {
    case 'UNSUPPORTED_TYPE':
      return 'JPEG, PNG, WEBP 이미지만 선택할 수 있어요.';
    case 'TOO_LARGE':
      return '사진 용량이 너무 커요. 다른 사진을 선택해 주세요.';
    case 'DECODE_FAILED':
    default:
      return '사진을 읽지 못했어요. 다른 사진을 선택해 주세요.';
  }
}
