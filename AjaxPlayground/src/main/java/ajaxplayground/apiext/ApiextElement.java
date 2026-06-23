package ajaxplayground.apiext;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;

/**
 * The parsed model of one element's {@code .apiext} file - the extended API format that carries
 * everything the hand-written HTML element reference shows (role, per-binding types + docs,
 * passthrough, validations), on top of the structural {@code .api} payload.
 * <p>
 * This is a display-first model: the reference page renders from it. It is read by name + framework
 * via the WO resource manager, so a {@code .apiext} in a framework's {@code src/main/components/}
 * (alongside its {@code .api}) is found without any filesystem plumbing.
 */
public class ApiextElement {

	public static class Binding {
		public String name;
		/** Direction-agnostic types declared directly under <binding> (the simple/legacy .api shape). */
		public final List<String> types = new ArrayList<>();
		/** Types the binding PULLS (reads/displays). Presence => the binding is read. */
		public final List<String> pullTypes = new ArrayList<>();
		/** Types the binding PUSHES (writes back). Presence => the binding is written. */
		public final List<String> pushTypes = new ArrayList<>();
		public String doc;
		public boolean required;
		public String defaults;

		/** The doc rendered from the Markdown subset to safe HTML (inline only - bindings rarely fence). */
		public String docHtml() {
			return Markdown.toHtml(doc);
		}

		public boolean pulls() { return !pullTypes.isEmpty(); }
		public boolean pushes() { return !pushTypes.isEmpty(); }

		/** Pull type(s), shortened + joined (e.g. "String | List"). */
		public String displayPullType() { return shortJoin(pullTypes); }
		/** Push type(s), shortened + joined. */
		public String displayPushType() { return shortJoin(pushTypes); }

		/**
		 * The type to show in the binding table's Type column. Prefers the directional types: a pull-only
		 * binding shows its pull type, a push-only its push type, a two-way binding whose pull and push
		 * types match shows that one (the common case), and a true split shows "Object → String". Falls
		 * back to direction-agnostic &lt;type&gt; for legacy/.api bindings.
		 */
		public String displayType() {
			String pull = displayPullType();
			String push = displayPushType();
			if (!pull.isEmpty() && !push.isEmpty()) {
				return pull.equals(push) ? pull : pull + " → " + push;
			}
			if (!pull.isEmpty()) { return pull; }
			if (!push.isEmpty()) { return push; }
			return shortJoin(types);
		}

		/** A short directionality label, e.g. "pull", "push", "pull / push", or "" if unknown. */
		public String directionLabel() {
			if (pulls() && pushes()) { return "pull / push"; }
			if (pulls()) { return "pull"; }
			if (pushes()) { return "push"; }
			return "";
		}

		/** An arrow glyph for the direction: ↓ pulled (read), ↑ pushed (written), ↕ both, "" if unknown. */
		public String directionArrow() {
			if (pulls() && pushes()) { return "↕"; } // ↕
			if (pulls()) { return "↓"; }             // ↓
			if (pushes()) { return "↑"; }            // ↑
			return "";
		}

		/** Hover/title text spelling out the direction (so the glyph is self-explaining). */
		public String directionTitle() {
			if (pulls() && pushes()) { return "Two-way: pulled (read) and pushed (written back)"; }
			if (pulls()) { return "Pulled (read by the element)"; }
			if (pushes()) { return "Pushed (written back by the element)"; }
			return "";
		}

		public boolean hasDirection() { return pulls() || pushes(); }

		private static String shortJoin(List<String> fqns) {
			if (fqns.isEmpty()) {
				return "";
			}
			List<String> shortNames = new ArrayList<>();
			for (String t : fqns) {
				int dot = t.lastIndexOf('.');
				shortNames.add(dot == -1 ? t : t.substring(dot + 1));
			}
			return String.join(" | ", shortNames);
		}
	}

	/** A constraint across bindings, carried verbatim from the .api: a message plus the predicates whose
	 *  simultaneous truth makes it fire (e.g. two {@code bound} predicates = "only one of these"). */
	public static class Validation {
		public String message;
		/** Each predicate is "bound:name" or "unbound:name" - kept as a human string for display. */
		public final List<String> predicates = new ArrayList<>();

		public String message() { return message; }
		public List<String> predicates() { return predicates; }
	}

	public String className;
	public boolean passthrough;
	public String doc;
	public final List<Binding> bindings = new ArrayList<>();
	public final List<Validation> validations = new ArrayList<>();

	public String className() { return className; }
	public boolean passthrough() { return passthrough; }
	public String doc() { return doc; }
	/** The element doc rendered from the Markdown subset to safe HTML (paragraphs + fenced code samples). */
	public String docHtml() { return Markdown.toHtml(doc); }
	public List<Binding> bindings() { return bindings; }
	public List<Validation> validations() { return validations; }
	public boolean hasValidations() { return !validations.isEmpty(); }

	/**
	 * Read and parse {@code <elementName>.apiext} from the given framework's components folder.
	 *
	 * @param elementName the element's simple name (e.g. "AjaxUpdateContainer")
	 * @param frameworkName the owning framework (e.g. "AjaxSlim")
	 * @return the parsed model, or null if the file is absent or unparseable
	 */
	public static ApiextElement load(String elementName, String frameworkName) {
		byte[] bytes = WOApplication.application().resourceManager()
				.bytesForResourceNamed(elementName + ".apiext", frameworkName, NSArray.emptyArray());
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(bytes));
			return parse(doc);
		}
		catch (Exception e) {
			return null;
		}
	}

	private static ApiextElement parse(Document doc) {
		Element wo = firstChildElement(doc.getDocumentElement(), "wo");
		if (wo == null) {
			return null;
		}
		ApiextElement out = new ApiextElement();
		out.className = wo.getAttribute("class");
		out.passthrough = "true".equals(wo.getAttribute("passthrough"));
		out.doc = textOf(firstChildElement(wo, "doc"));

		for (Element b : childElements(wo, "binding")) {
			Binding binding = new Binding();
			binding.name = b.getAttribute("name");
			binding.required = "true".equals(b.getAttribute("required"));
			binding.defaults = b.hasAttribute("defaults") ? b.getAttribute("defaults") : null;
			// Direction-agnostic types directly under <binding> (legacy/.api shape).
			for (Element ty : childElements(b, "type")) {
				binding.types.add(textOf(ty));
			}
			// Directional types: <pull>/<push> blocks, each holding <type>(s).
			Element pull = firstChildElement(b, "pull");
			if (pull != null) {
				for (Element ty : childElements(pull, "type")) {
					binding.pullTypes.add(textOf(ty));
				}
			}
			Element push = firstChildElement(b, "push");
			if (push != null) {
				for (Element ty : childElements(push, "type")) {
					binding.pushTypes.add(textOf(ty));
				}
			}
			binding.doc = textOf(firstChildElement(b, "doc"));
			out.bindings.add(binding);
		}

		for (Element v : childElements(wo, "validation")) {
			Validation validation = new Validation();
			validation.message = v.getAttribute("message");
			for (Element p : childElements(v, "bound")) {
				validation.predicates.add("bound: " + p.getAttribute("name"));
			}
			for (Element p : childElements(v, "unbound")) {
				validation.predicates.add("unbound: " + p.getAttribute("name"));
			}
			out.validations.add(validation);
		}

		return out;
	}

	// --- tiny DOM helpers (no streams; keep the dependency surface nil) ---------------------------

	private static Element firstChildElement(Node parent, String name) {
		List<Element> els = childElements(parent, name);
		return els.isEmpty() ? null : els.get(0);
	}

	private static List<Element> childElements(Node parent, String name) {
		List<Element> out = new ArrayList<>();
		if (parent == null) {
			return out;
		}
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node n = children.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getNodeName())) {
				out.add((Element) n);
			}
		}
		return out;
	}

	private static String textOf(Element el) {
		if (el == null) {
			return null;
		}
		String t = el.getTextContent();
		return t == null ? null : t.strip();
	}
}
