package com.survey.meetorsolo.external.tourapi.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

@Component
public class TourApiResponseParser {

    private static final String SUCCESS_RESULT_CODE = "0000";

    private final ObjectMapper objectMapper;

    public TourApiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> TourApiPage<T> parsePage(String responseBody, Class<T> itemType) {
        if (responseBody == null || responseBody.isBlank()) {
            throw malformed("응답 본문이 비어 있습니다.", null);
        }

        String trimmedBody = responseBody.stripLeading();
        if (trimmedBody.startsWith("<")) {
            throw parseXmlError(responseBody);
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode response = root.path("response");
            JsonNode header = response.path("header");
            String resultCode = textValue(header, "resultCode");
            if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
                throw remoteError(resultCode);
            }

            JsonNode body = response.path("body");
            if (!body.isObject()) {
                throw malformed("응답 body 형식이 올바르지 않습니다.", null);
            }

            List<T> items = parseItems(body.path("items"), itemType);
            return new TourApiPage<>(
                    intValue(body, "numOfRows"),
                    intValue(body, "pageNo"),
                    intValue(body, "totalCount"),
                    items
            );
        } catch (TourApiClientException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw malformed("JSON 응답을 해석할 수 없습니다.", exception);
        }
    }

    private <T> List<T> parseItems(JsonNode itemsNode, Class<T> itemType)
            throws JsonProcessingException {
        if (itemsNode.isMissingNode() || itemsNode.isNull()
                || (itemsNode.isTextual() && itemsNode.asText().isBlank())) {
            return List.of();
        }
        if (!itemsNode.isObject()) {
            throw malformed("items 형식이 올바르지 않습니다.", null);
        }

        JsonNode itemNode = itemsNode.path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()
                || (itemNode.isTextual() && itemNode.asText().isBlank())) {
            return List.of();
        }

        List<T> result = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) {
                result.add(objectMapper.treeToValue(node, itemType));
            }
            return result;
        }
        if (itemNode.isObject()) {
            result.add(objectMapper.treeToValue(itemNode, itemType));
            return result;
        }
        throw malformed("item 형식이 올바르지 않습니다.", null);
    }

    private TourApiClientException parseXmlError(String responseBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            var document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(responseBody)));
            String remoteCode = nodeText(document, "returnReasonCode");
            if (remoteCode == null) {
                throw malformed("XML 응답 형식이 올바르지 않습니다.", null);
            }
            return remoteError(remoteCode);
        } catch (TourApiClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw malformed("XML 오류 응답을 해석할 수 없습니다.", exception);
        }
    }

    private String nodeText(org.w3c.dom.Document document, String tagName) {
        var nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        String value = field.asText();
        return value.isBlank() ? null : value;
    }

    private int intValue(JsonNode node, String fieldName) {
        String value = textValue(node, fieldName);
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private TourApiClientException remoteError(String remoteCode) {
        TourApiErrorType errorType = switch (remoteCode == null ? "" : remoteCode) {
            case "20", "30", "31", "32" -> TourApiErrorType.AUTHORIZATION;
            case "22" -> TourApiErrorType.RATE_LIMIT;
            case "12" -> TourApiErrorType.CONFIGURATION;
            default -> TourApiErrorType.REMOTE;
        };
        String safeCode = remoteCode == null ? "UNKNOWN" : remoteCode;
        return TourApiClientException.forRemoteError(errorType, safeCode);
    }

    private TourApiClientException malformed(String detail, Throwable cause) {
        if (cause == null) {
            return TourApiClientException.withDetail(
                    TourApiErrorType.MALFORMED_RESPONSE,
                    detail
            );
        }
        return TourApiClientException.withDetail(
                TourApiErrorType.MALFORMED_RESPONSE,
                detail,
                cause
        );
    }
}
