package com.runab.api.service.source;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/** Produces readable source text without executing HTML or dropping repeated clauses. */
public final class BizinfoSourceTextParser {

    private BizinfoSourceTextParser() {}

    public static String toText(String html) {
        if (html == null || html.isBlank()) return null;

        var document = Jsoup.parse(html);
        document.select("script, style").remove();
        var text = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    text.append(textNode.getWholeText().replaceAll("[\\s\\u00a0]+", " "));
                } else if (node instanceof Element element) {
                    if (element.normalName().equals("td") || element.normalName().equals("th")) {
                        text.append(' ');
                    } else if (element.isBlock() || element.normalName().equals("br")) {
                        text.append('\n');
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && element.isBlock()
                        && !element.normalName().equals("td") && !element.normalName().equals("th")) {
                    text.append('\n');
                }
            }
        }, document.body());

        String normalized = text.toString().replaceAll("[\\p{Zs}\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n").replaceAll("\\n{2,}", "\n").trim();
        return normalized.isBlank() ? null : normalized;
    }
}
