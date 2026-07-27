package com.jd.genie.service.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OcrTextNormalizerTest {
    @Test
    void stripsGeometryPrefixButPreservesLineBreaks() {
        String raw = "150,40,71,261,90,应用安全信息时出错\n"
                + "394,244,71,475,90,将安全信息应用到以下对象时发生错误:";

        assertEquals("应用安全信息时出错\n将安全信息应用到以下对象时发生错误:", OcrTextNormalizer.normalize(raw));
    }

    @Test
    void keepsOrdinaryNumericTextUntouched() {
        assertEquals("2026,07,27", OcrTextNormalizer.normalize("2026,07,27"));
    }
}