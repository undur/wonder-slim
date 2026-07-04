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
 * passthrough, constraints), on top of the structural {@code .api} payload.
 * <p>
 * This is a display-first model: the reference page renders from it. It is read by name + framework
 * via the WO resource manager, so a {@code .apiext} in a framework's {@code src/main/components/}
 * (alongside its {@code .api}) is found without any filesystem plumbing.
 */
public class ApiextElement {

	/**
	 * One accepted type for a binding: a fully-qualified Java class (the validatable constraint) plus an
	 * optional interpretation (a reading rule that does NOT change the type, e.g. "truthy"). The
	 * interpretation is shown as a qualifier - e.g. "Object (truthy)" - but validation uses the class.
	 */
	public static class Type {
		public final String fqn;
		public final String interpretation; // null, or e.g. "truthy"

		public Type(String fqn, String interpretation) {
			this.fqn = fqn;
			this.interpretation = interpretation;
		}

		/** Short class name + interpretation qualifier, e.g. "String", "Object (truthy)". */
		public String display() {
			int dot = fqn == null ? -1 : fqn.lastIndexOf('.');
			String shortName = (fqn == null) ? "" : (dot == -1 ? fqn : fqn.substring(dot + 1));
			return (interpretation == null || interpretation.isEmpty()) ? shortName : shortName + " (" + interpretation + ")";
		}
	}

	public static class Binding {
		public String name;
		/** Types the binding PULLS (reads/displays). Presence => the binding is read. */
		public final List<Type> pullTypes = new ArrayList<>();
		/** Types the binding PUSHES (writes back). Presence => the binding is written. */
		public final List<Type> pushTypes = new ArrayList<>();
		public String doc;
		public boolean required;
		public String defaults;
		/**
		 * The binding's declared default (the literal the element behaves as when the binding is unbound),
		 * or null for "no declared default". Display-only here: a default never satisfies required or any
		 * constraint. Not to be confused with {@link #defaults}, the legacy autocomplete-preset hint.
		 */
		public String defaultValue;

		public boolean hasDefault() { return defaultValue != null; }

		/** The doc rendered from the Markdown subset to safe HTML (inline only - bindings rarely fence). */
		public String docHtml() {
			return Markdown.toHtml(doc);
		}

		public boolean pulls() { return !pullTypes.isEmpty(); }
		public boolean pushes() { return !pushTypes.isEmpty(); }
		public boolean hasDirection() { return pulls() || pushes(); }

		/** Pull type(s), shortened + joined (e.g. "String | List"). */
		public String displayPullType() { return shortJoin(pullTypes); }
		/** Push type(s), shortened + joined. */
		public String displayPushType() { return shortJoin(pushTypes); }

		/**
		 * True when the binding is two-way AND its pull and push types differ (e.g. pulls Object, pushes
		 * String). The renderer shows a split like this as two stacked rows (↓ pull / ↑ push) instead of
		 * the single-glyph form, which gets confusing once the types are long or multi-valued.
		 */
		public boolean typeSplit() {
			return pulls() && pushes() && !displayPullType().equals(displayPushType());
		}

		/**
		 * The single-row type display: the directional type when one suffices (pull-only, push-only, or a
		 * two-way binding whose pull and push types match). NOT used for the split case -
		 * {@link #typeSplit()} drives the two-row form there.
		 */
		public String displayType() {
			String pull = displayPullType();
			return !pull.isEmpty() ? pull : displayPushType();  // same-type two-way resolves via pull (pull==push)
		}

		// --- direction markers (shared by the single-glyph form and the per-row split form) -----------

		/** The whole-binding arrow: ↕ two-way, ↓ pull-only, ↑ push-only, "" if unknown. */
		public String directionArrow() { return arrowFor(pulls(), pushes()); }
		/** The whole-binding marker CSS class (colour by behaviour: push=orange, both=red, pull=faint). */
		public String directionClass() { return classFor(pulls(), pushes()); }
		/** Hover text spelling out the whole-binding direction. */
		public String directionTitle() { return titleFor(pulls(), pushes()); }

		/** Per-row markers for the split form: the pull row (↓, faint) ... */
		public String pullArrow() { return arrowFor(true, false); }
		public String pullClass() { return classFor(true, false); }
		public String pullTitle() { return titleFor(true, false); }
		/** ... and the push row (↑, orange). */
		public String pushArrow() { return arrowFor(false, true); }
		public String pushClass() { return classFor(false, true); }
		public String pushTitle() { return titleFor(false, true); }

		private static String arrowFor(boolean pull, boolean push) {
			if (pull && push) { return "↕"; } // ↕
			if (pull)         { return "↓"; } // ↓
			if (push)         { return "↑"; } // ↑
			return "";
		}

		private static String classFor(boolean pull, boolean push) {
			if (pull && push) { return "dir dir-both"; } // two-way: most notable (red)
			if (push)         { return "dir dir-push"; } // writes back: notable (orange)
			return "dir dir-pull";                       // read-only: the safe norm (faint)
		}

		private static String titleFor(boolean pull, boolean push) {
			if (pull && push) { return "Two-way: pulled (read) and pushed (written back)"; }
			if (push)         { return "Pushed (written back by the element)"; }
			return "Pulled (read by the element)";
		}

		private static String shortJoin(List<Type> typeList) {
			if (typeList.isEmpty()) {
				return "";
			}
			List<String> rendered = new ArrayList<>();
			for (Type t : typeList) {
				rendered.add(t.display());
			}
			return String.join(" | ", rendered);
		}
	}

	/**
	 * A typed cross-binding constraint ({@code <choose>} / {@code <requires>}) with its human message.
	 * The message is GENERATED from the typed rule - the {@code message} attribute, when present,
	 * overrides it. This is the display payoff of typed constraints: uniform phrasing with no
	 * hand-written drift, and (later) localizable by the consumer.
	 */
	public static class Constraint {
		public final String kind;    // "choose" or "requires" (available for styling)
		public final String message;

		public Constraint(String kind, String message) {
			this.kind = kind;
			this.message = message;
		}

		public String kind() { return kind; }
		public String message() { return message; }
	}

	public String className;
	/**
	 * The element's unknown-attribute policy ("forbidden" | "allowed" | "passthrough"), or null when the
	 * author stated no policy — absent means the consuming tool decides, so null must never be defaulted
	 * to one of the three values.
	 */
	public String unknownAttributes;
	public String doc;
	public final List<Binding> bindings = new ArrayList<>();
	public final List<Constraint> constraints = new ArrayList<>();

	public String className() { return className; }
	public boolean passthrough() { return "passthrough".equals(unknownAttributes); }
	public boolean forbidden() { return "forbidden".equals(unknownAttributes); }
	public String doc() { return doc; }
	/** The element doc rendered from the Markdown subset to safe HTML (paragraphs + fenced code samples). */
	public String docHtml() { return Markdown.toHtml(doc); }
	public List<Binding> bindings() { return bindings; }
	public List<Constraint> constraints() { return constraints; }
	public boolean hasConstraints() { return !constraints.isEmpty(); }

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
		out.unknownAttributes = wo.hasAttribute("unknownAttributes") ? wo.getAttribute("unknownAttributes") : null;
		out.doc = textOf(firstChildElement(wo, "doc"));

		for (Element b : childElements(wo, "binding")) {
			Binding binding = new Binding();
			binding.name = b.getAttribute("name");
			binding.required = "true".equals(b.getAttribute("required"));
			binding.defaults = b.hasAttribute("defaults") ? b.getAttribute("defaults") : null;
			binding.defaultValue = textOf(firstChildElement(b, "default"));
			// Types live in <pull>/<push> blocks (a type always has a direction).
			Element pull = firstChildElement(b, "pull");
			if (pull != null) {
				for (Element ty : childElements(pull, "type")) {
					binding.pullTypes.add(typeOf(ty));
				}
			}
			Element push = firstChildElement(b, "push");
			if (push != null) {
				for (Element ty : childElements(push, "type")) {
					binding.pushTypes.add(typeOf(ty));
				}
			}
			binding.doc = textOf(firstChildElement(b, "doc"));
			out.bindings.add(binding);
		}

		// Constraints in DOCUMENT order (their order is presentation order, like bindings) - so one walk
		// over wo's children dispatching by tag, not per-tag sweeps.
		NodeList woChildren = wo.getChildNodes();
		for (int i = 0; i < woChildren.getLength(); i++) {
			Node n = woChildren.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			if ("choose".equals(n.getNodeName())) {
				out.constraints.add(parseChoose((Element) n));
			}
			else if ("requires".equals(n.getNodeName())) {
				out.constraints.add(parseRequires((Element) n));
			}
		}

		return out;
	}

	// --- constraint parsing + message generation ---------------------------------------------------

	/** {@code <choose min max>}: cardinality over alternatives (bindings or any-of groups). */
	private static Constraint parseChoose(Element c) {
		String override = c.hasAttribute("message") ? c.getAttribute("message") : null;
		Integer min = intAttr(c, "min");
		Integer max = intAttr(c, "max");

		// Render each alternative: 'name', or a grouped ('a' or 'b') for an any-of.
		List<String> alts = new ArrayList<>();
		NodeList children = c.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node n = children.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			if ("binding".equals(n.getNodeName())) {
				alts.add(quoted(((Element) n).getAttribute("name")));
			}
			else if ("any-of".equals(n.getNodeName())) {
				alts.add("(" + orJoin(memberNames((Element) n)) + ")");
			}
		}

		String message = override != null ? override : chooseMessage(min, max, listJoin(alts));
		return new Constraint("choose", message);
	}

	private static String chooseMessage(Integer min, Integer max, String list) {
		if (min != null && max != null) {
			if (min == 1 && max == 1) {
				return "Exactly one of " + list + " must be bound.";
			}
			if (min.equals(max)) {
				return "Exactly " + min + " of " + list + " must be bound.";
			}
			return "Between " + min + " and " + max + " of " + list + " must be bound.";
		}
		if (max != null) {
			return (max == 1 ? "At most one of " : "At most " + max + " of ") + list + " may be bound.";
		}
		if (min != null) {
			return (min == 1 ? "At least one of " : "At least " + min + " of ") + list + " must be bound.";
		}
		return "Choose among " + list + "."; // min/max both absent is invalid per the format; degrade readably
	}

	/** {@code <requires binding must when>}: implication - antecedent bound => obligation on the binding. */
	private static Constraint parseRequires(Element r) {
		String override = r.hasAttribute("message") ? r.getAttribute("message") : null;
		String subject = quoted(r.getAttribute("binding"));
		String must = r.hasAttribute("must") ? r.getAttribute("must") : "bound";

		String antecedent = null; // null = unconditional (settable/gettable only, per the format)
		if (r.hasAttribute("when")) {
			antecedent = quoted(r.getAttribute("when"));
		}
		else {
			Element anyOf = firstChildElement(r, "any-of");
			if (anyOf != null) {
				antecedent = orJoin(memberNames(anyOf));
			}
		}
		String whenClause = antecedent == null ? "" : " when " + antecedent + " is bound";

		String message;
		switch (must) {
			case "settable" -> message = subject + " must be settable (assignable, not a constant)" + whenClause + ".";
			case "gettable" -> message = subject + " must be a keypath, not a constant" + whenClause + ".";
			default         -> message = subject + " must be bound" + whenClause + ".";
		}
		return new Constraint("requires", override != null ? override : message);
	}

	private static List<String> memberNames(Element anyOf) {
		List<String> names = new ArrayList<>();
		for (Element b : childElements(anyOf, "binding")) {
			names.add(quoted(b.getAttribute("name")));
		}
		return names;
	}

	private static String quoted(String name) {
		return "'" + name + "'";
	}

	/** "a', 'b' or 'c" style join for disjunctions (members already quoted). */
	private static String orJoin(List<String> quotedNames) {
		if (quotedNames.size() <= 1) {
			return quotedNames.isEmpty() ? "" : quotedNames.get(0);
		}
		return String.join(", ", quotedNames.subList(0, quotedNames.size() - 1)) + " or " + quotedNames.get(quotedNames.size() - 1);
	}

	/** "'a', 'b' and 'c'" style join for the choose alternative list (members already quoted/grouped). */
	private static String listJoin(List<String> alts) {
		if (alts.size() <= 1) {
			return alts.isEmpty() ? "" : alts.get(0);
		}
		return String.join(", ", alts.subList(0, alts.size() - 1)) + " and " + alts.get(alts.size() - 1);
	}

	private static Integer intAttr(Element el, String name) {
		if (!el.hasAttribute(name)) {
			return null;
		}
		try {
			return Integer.valueOf(el.getAttribute(name).trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
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

	/** Build a Type from a <type> element: its text is the FQN, its optional `interpretation` the rule. */
	private static Type typeOf(Element ty) {
		String interp = ty.hasAttribute("interpretation") ? ty.getAttribute("interpretation") : null;
		return new Type(textOf(ty), interp);
	}
}
