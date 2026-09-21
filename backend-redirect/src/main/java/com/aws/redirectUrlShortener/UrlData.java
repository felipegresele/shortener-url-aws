package com.aws.redirectUrlShortener;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UrlData {

    private String originalUrl;

    private long expirationTime;

}
