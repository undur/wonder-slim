package ajaxplayground.apiext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small, injection-safe Markdown subset for {@code .apiext} doc fields. Supports exactly:
 * <ul>
 * <li>fenced code blocks <code>```lang … ```</code> → {@code <pre><code class="lang-…">}</li>
 * <li>inline code <code>`x`</code> → {@code <code>}</li>
 * <li><code>**bold**</code> → {@code <strong>}</li>
 * <li><code>*italic*</code> → {@code <em>}</li>
 * <li><code>[text](url)</code> → {@code <a href>}</li>
 * <li>blank-line-separated paragraphs → {@code <p>}</li>
 * </ul>
 *
 * <p>
 * <b>Safe by construction:</b> the whole input is HTML-escaped FIRST, then only these whitelisted
 * transforms run over the escaped text. There is no path for author-supplied raw HTML to reach the page -
 * unlike "render arbitrary Markdown then sanitise", only the constructs implemented here exist. No
 * dependency; it is a handful of passes, which is all binding/element docs need.
 * </p>
 */
public class Markdown {

	private Markdown() {
	}

	private static final Pattern FENCE = Pattern.compile("```([a-zA-Z0-9_-]*)\\r?\\n([\\s\\S]*?)```");
	private static final Pattern PLACEHOLDER = Pattern.compile("__MDCB(\\d+)__");
	private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
	private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
	private static final Pattern ITALIC = Pattern.compile("\\*([^*\\n]+)\\*");
	private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");

	/** Render the MD subset to safe HTML. Null/blank input yields an empty string. */
	public static String toHtml(String md) {
		if (md == null || md.isBlank()) {
			return "";
		}
		// 1. Escape EVERYTHING first - from here on the string contains no live HTML.
		String escaped = escape(md.strip());

		// 2. Pull fenced code blocks OUT to placeholders, so their contents are never touched by the
		//    inline passes (backticks/asterisks inside a code sample must stay literal). The placeholder
		//    is surrounded by blank lines so it becomes its own paragraph (a <pre> not wrapped in a <p>).
		StringBuilder work = new StringBuilder();
		List<String> blocks = new ArrayList<>();
		Matcher fm = FENCE.matcher(escaped);
		int last = 0;
		while (fm.find()) {
			work.append(escaped, last, fm.start());
			String lang = fm.group(1);
			String code = fm.group(2);
			String cls = lang.isEmpty() ? "" : " class=\"lang-" + lang + "\"";
			blocks.add("<pre class=\"md-code\"><code" + cls + ">" + code + "</code></pre>");
			work.append("\n\n__MDCB").append(blocks.size() - 1).append("__\n\n");
			last = fm.end();
		}
		work.append(escaped.substring(last));
		String body = work.toString();

		// 3. Inline transforms over the fence-free text (order: code, bold, italic, link).
		body = INLINE_CODE.matcher(body).replaceAll("<code>$1</code>");
		body = BOLD.matcher(body).replaceAll("<strong>$1</strong>");
		body = ITALIC.matcher(body).replaceAll("<em>$1</em>");
		body = LINK.matcher(body).replaceAll("<a href=\"$2\">$1</a>");

		// 4. Paragraphs: split on blank lines. A chunk that is JUST a code-block placeholder is emitted
		//    bare (the <pre> stands alone); every other chunk becomes a <p>, with single newlines -> <br/>.
		StringBuilder out = new StringBuilder();
		for (String para : body.split("\\r?\\n\\s*\\r?\\n")) {
			String t = para.strip();
			if (t.isEmpty()) {
				continue;
			}
			if (t.matches("__MDCB\\d+__")) {
				out.append(restore(t, blocks));
			}
			else {
				out.append("<p>").append(restore(t.replace("\n", "<br/>"), blocks)).append("</p>");
			}
		}
		return out.toString();
	}

	private static String restore(String s, List<String> blocks) {
		Matcher m = PLACEHOLDER.matcher(s);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(sb, Matcher.quoteReplacement(blocks.get(Integer.parseInt(m.group(1)))));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
