package com.runab.api.service.source;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BizinfoSourceTextParserTest {

    @Test
    void preservesParagraphsBreaksAndListItems() throws Exception {
        assertThat(parse("<p>지원 대상<br>소상공인</p><p>지원 내용</p><ul><li>자금</li><li>교육</li></ul>"))
                .isEqualTo("지원 대상\n소상공인\n지원 내용\n자금\n교육");
    }

    @Test
    void separatesTableCellsAndRows() throws Exception {
        assertThat(parse("<table><tr><th>구분</th><th>금액</th></tr><tr><td>기업당</td><td>100만원</td></tr></table>"))
                .isEqualTo("구분 금액\n기업당 100만원");
    }

    @Test
    void decodesEntitiesWithoutRemovingRepeatedClauses() throws Exception {
        assertThat(parse("<p>R&amp;D&nbsp;지원 &lt;필수&gt; &#169;</p><p>같은 조건</p><p>같은 조건</p>"))
                .isEqualTo("R&D 지원 <필수> ©\n같은 조건\n같은 조건");
    }

    @Test
    void excludesScriptsStylesAndNormalizesWhitespace() throws Exception {
        assertThat(parse("<style>body { color: red; }</style><script>alert('bad')</script><p>  공식   내용 </p>\n<p> 둘째\t줄 </p>"))
                .isEqualTo("공식 내용\n둘째 줄");
    }

    @Test
    void leavesMissingOrEmptySourceUnknown() throws Exception {
        assertThat(parse(null)).isNull();
        assertThat(parse("  ")).isNull();
        assertThat(parse("<p>&nbsp;</p><script>bad()</script>")).isNull();
    }

    private String parse(String html) {
        return BizinfoSourceTextParser.toText(html);
    }
}
