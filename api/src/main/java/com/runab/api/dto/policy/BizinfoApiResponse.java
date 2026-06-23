package com.runab.api.dto.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * bizinfo API 최상위 응답 래퍼.
 * 응답 형식: {"jsonArray": [ {정책}, {정책}, ... ]}
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BizinfoApiResponse {

    private List<BizinfoPolicyItem> jsonArray;
}
