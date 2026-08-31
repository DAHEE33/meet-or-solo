import { describe, expect, it } from 'vitest';
import { positiveInteger, readNumberFromLocationState } from './positiveInteger';

describe('positiveInteger', () => {
  it('양의 정수 문자열과 숫자를 그대로 반환한다', () => {
    expect(positiveInteger(7)).toBe(7);
    expect(positiveInteger('7')).toBe(7);
  });

  it('0 이하, 소수, 숫자로 해석되지 않는 값은 null이다', () => {
    expect(positiveInteger(0)).toBeNull();
    expect(positiveInteger(-1)).toBeNull();
    expect(positiveInteger(1.5)).toBeNull();
    expect(positiveInteger('abc')).toBeNull();
    expect(positiveInteger(null)).toBeNull();
    expect(positiveInteger(undefined)).toBeNull();
  });
});

describe('readNumberFromLocationState', () => {
  it('state 객체에서 지정한 key의 양의 정수 값을 읽는다', () => {
    expect(readNumberFromLocationState({ festivalId: 144 }, 'festivalId')).toBe(144);
  });

  it('key가 없거나 state가 객체가 아니면 null이다', () => {
    expect(readNumberFromLocationState({ other: 1 }, 'festivalId')).toBeNull();
    expect(readNumberFromLocationState(null, 'festivalId')).toBeNull();
    expect(readNumberFromLocationState(undefined, 'festivalId')).toBeNull();
    expect(readNumberFromLocationState('string', 'festivalId')).toBeNull();
  });

  it('key의 값이 유효한 양의 정수가 아니면 null이다', () => {
    expect(readNumberFromLocationState({ festivalId: 0 }, 'festivalId')).toBeNull();
    expect(readNumberFromLocationState({ festivalId: 'abc' }, 'festivalId')).toBeNull();
  });
});
