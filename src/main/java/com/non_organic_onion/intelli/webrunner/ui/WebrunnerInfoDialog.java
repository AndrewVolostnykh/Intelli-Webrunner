package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JEditorPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.JTree;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Non-modal, read-only help dialog backed by the same Markdown docs used for GitHub Pages.
 */
public final class WebrunnerInfoDialog {

	private static final String DOC_RESOURCE_DIR = "docs/";
	private static final List<DocFile> DOC_FILES = List.of(
		new DocFile("index.md", "Home"),
		new DocFile("quickstart.md", "Quickstart"),
		new DocFile("http.md", "HTTP Requests"),
		new DocFile("grpc.md", "gRPC Requests"),
		new DocFile("request-editor.md", "Headers, Params, Body"),
		new DocFile("response-viewer.md", "Response Viewer"),
		new DocFile("scripting.md", "Scripting"),
		new DocFile("debug-call.md", "Debug Call"),
		new DocFile("chain.md", "Chain Mode"),
		new DocFile("import-export.md", "Import and Export"),
		new DocFile("dev-tools.md", "Dev Tools"),
		new DocFile("faq.md", "FAQ")
	);

	private WebrunnerInfoDialog() {
	}

	public static void show(Component parent) {
		JFrame dialog = TaskbarWindowSupport.createFrame("Webrunner Info", parent);
		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(buildDocsBrowser(), BorderLayout.CENTER);
		dialog.setSize(1100, 760);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private static JComponent buildDocsBrowser() {
		Map<String, DocPage> pages = loadDocs();
		DefaultMutableTreeNode root = buildDocsTree(pages);
		JTree tree = new JTree(root);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		for (int row = 0; row < tree.getRowCount(); row++) {
			tree.expandRow(row);
		}

		JEditorPane details = new JEditorPane("text/html", "");
		details.setEditable(false);
		details.setOpaque(true);
		details.addHyperlinkListener(event -> {
			if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
				return;
			}
			String target = event.getDescription();
			String docName = normalizeDocLink(target);
			if (docName != null && pages.containsKey(docName)) {
				selectDoc(tree, docName);
				return;
			}
			if (target != null && !target.isBlank()) {
				BrowserUtil.browse(target);
			}
		});

		tree.addTreeSelectionListener((TreeSelectionEvent event) -> {
			DefaultMutableTreeNode node = selectedNode(event.getPath());
			Object value = node == null ? null : node.getUserObject();
			if (value instanceof DocPage doc) {
				details.setText(page(doc.html()));
				details.setCaretPosition(0);
			} else if (value instanceof DocSection section) {
				DocPage doc = pages.get(section.docName());
				if (doc != null) {
					details.setText(page(doc.html()));
					SwingUtilities.invokeLater(() -> details.scrollToReference(section.anchor()));
				}
			}
		});
		selectDoc(tree, "index.md");

		JSplitPane splitPane = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			new JBScrollPane(tree),
			new JBScrollPane(details)
		);
		splitPane.setResizeWeight(0.28);
		SplitPaneStyling.applyThinBlackDivider(splitPane);
		return splitPane;
	}

	private static Map<String, DocPage> loadDocs() {
		Map<String, DocPage> pages = new LinkedHashMap<>();
		for (DocFile file : DOC_FILES) {
			String markdown = readResource(DOC_RESOURCE_DIR + file.name());
			String title = firstHeading(markdown, file.title());
			pages.put(file.name(), new DocPage(
				file.name(),
				title,
				MarkdownRenderer.render(markdown),
				extractSections(file.name(), markdown)
			));
		}
		return pages;
	}

	private static DefaultMutableTreeNode buildDocsTree(Map<String, DocPage> pages) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Docs");
		for (DocFile file : DOC_FILES) {
			DocPage page = pages.get(file.name());
			if (page != null) {
				DefaultMutableTreeNode pageNode = new DefaultMutableTreeNode(page);
				if ("scripting.md".equals(file.name()) || "dev-tools.md".equals(file.name())) {
					for (DocSection section : page.sections()) {
						pageNode.add(new DefaultMutableTreeNode(section));
					}
				}
				root.add(pageNode);
			}
		}
		return root;
	}

	private static List<DocSection> extractSections(String docName, String markdown) {
		List<DocSection> sections = new ArrayList<>();
		if (markdown == null) {
			return sections;
		}
		for (String line : markdown.split("\\R")) {
			String trimmed = line.trim();
			int level = MarkdownRenderer.headingLevel(trimmed);
			if (level == 2) {
				String title = trimmed.substring(level).trim();
				sections.add(new DocSection(docName, title, MarkdownRenderer.anchor(title)));
			}
		}
		return sections;
	}

	private static void selectDoc(JTree tree, String docName) {
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
		for (int index = 0; index < root.getChildCount(); index++) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(index);
			Object value = child.getUserObject();
			if (value instanceof DocPage page && page.name().equals(docName)) {
				tree.setSelectionPath(new TreePath(child.getPath()));
				return;
			}
		}
		tree.setSelectionRow(0);
	}

	private static DefaultMutableTreeNode selectedNode(TreePath path) {
		if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) {
			return null;
		}
		return node;
	}

	private static String readResource(String resource) {
		InputStream stream = WebrunnerInfoDialog.class.getClassLoader().getResourceAsStream(resource);
		if (stream == null) {
			return "# Documentation unavailable\n\nMissing packaged resource `" + resource + "`.";
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			StringBuilder text = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				text.append(line).append('\n');
			}
			return text.toString();
		} catch (Exception error) {
			return "# Documentation unavailable\n\nCould not read `" + resource + "`: `" + error.getMessage() + "`.";
		}
	}

	private static String firstHeading(String markdown, String fallback) {
		if (markdown == null) {
			return fallback;
		}
		for (String line : markdown.split("\\R")) {
			if (line.startsWith("# ")) {
				return line.substring(2).trim();
			}
		}
		return fallback;
	}

	private static String normalizeDocLink(String target) {
		if (target == null || target.isBlank()) {
			return null;
		}
		String normalized = target.trim();
		int hash = normalized.indexOf('#');
		if (hash >= 0) {
			normalized = normalized.substring(0, hash);
		}
		if (normalized.isBlank()) {
			return null;
		}
		try {
			URI uri = URI.create(normalized);
			if (uri.isAbsolute()) {
				return null;
			}
		} catch (Exception ignored) {
			return null;
		}
		normalized = normalized.replace('\\', '/');
		int slash = normalized.lastIndexOf('/');
		if (slash >= 0) {
			normalized = normalized.substring(slash + 1);
		}
		return normalized.toLowerCase(Locale.ROOT).endsWith(".md") ? normalized : null;
	}

	private static String page(String body) {
		String background = color(JBColor.PanelBackground);
		String foreground = color(JBColor.foreground());
		String border = color(JBColor.border());
		String codeBackground = color(new JBColor(0xf5f5f5, 0x2b2d30));
		String link = color(new JBColor(0x0b5cad, 0x7ab7ff));
		return String.format("""
			<html>
			<head>
			  <style>
			    body {
			      font-family: sans-serif;
			      font-size: 12px;
			      margin: 14px;
			      color: %s;
			      background: %s;
			    }
			    h1 { font-size: 22px; margin: 0 0 12px 0; }
			    h2 { font-size: 17px; margin: 18px 0 8px 0; }
			    h3 { font-size: 14px; margin: 14px 0 6px 0; }
			    p { margin: 0 0 9px 0; line-height: 1.35; }
			    ul, ol { margin-top: 3px; margin-bottom: 10px; }
			    li { margin-bottom: 4px; }
			    a { color: %s; text-decoration: underline; }
			    code {
			      font-family: monospace;
			      background: %s;
			      padding: 1px 3px;
			    }
			    pre {
			      font-family: monospace;
			      font-size: 12px;
			      background: %s;
			      border: 1px solid %s;
			      padding: 8px;
			      margin: 8px 0 12px 0;
			    }
			    table { border-collapse: collapse; margin: 8px 0 12px 0; }
			    th, td { border: 1px solid %s; padding: 4px 7px; }
			    th { font-weight: bold; background: %s; }
			  </style>
			</head>
			<body>
			%s
			</body>
			</html>
			""", foreground, background, link, codeBackground, codeBackground, border, border, codeBackground, body);
	}

	private static String color(java.awt.Color color) {
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private record DocFile(String name, String title) {
	}

	private record DocPage(String name, String title, String html, List<DocSection> sections) {
		@Override
		public String toString() {
			return title;
		}
	}

	private record DocSection(String docName, String title, String anchor) {
		@Override
		public String toString() {
			return title;
		}
	}

	private static final class MarkdownRenderer {
		private MarkdownRenderer() {
		}

		private static String render(String markdown) {
			List<String> lines = List.of((markdown == null ? "" : markdown).split("\\R", -1));
			StringBuilder html = new StringBuilder();
			StringBuilder paragraph = new StringBuilder();
			boolean inCode = false;
			boolean inUl = false;
			boolean inOl = false;
			boolean inTable = false;
			for (int index = 0; index < lines.size(); index++) {
				String line = lines.get(index);
				String trimmed = line.trim();
				if (trimmed.startsWith("```")) {
					closeParagraph(html, paragraph);
					if (inCode) {
						html.append("</code></pre>\n");
						inCode = false;
					} else {
						closeLists(html, inUl, inOl);
						inUl = false;
						inOl = false;
						inTable = closeTable(html, inTable);
						html.append("<pre><code>");
						inCode = true;
					}
					continue;
				}
				if (inCode) {
					html.append(escape(line)).append('\n');
					continue;
				}
				if (trimmed.isBlank()) {
					closeParagraph(html, paragraph);
					closeLists(html, inUl, inOl);
					inUl = false;
					inOl = false;
					inTable = closeTable(html, inTable);
					continue;
				}
				if (isTableRow(trimmed)) {
					closeParagraph(html, paragraph);
					closeLists(html, inUl, inOl);
					inUl = false;
					inOl = false;
					if (index + 1 < lines.size() && isTableSeparator(lines.get(index + 1).trim())) {
						if (!inTable) {
							html.append("<table>\n");
							inTable = true;
						}
						appendTableRow(html, trimmed, true);
						index++;
					} else if (inTable) {
						appendTableRow(html, trimmed, false);
					} else {
						appendParagraphText(paragraph, trimmed);
					}
					continue;
				}
				inTable = closeTable(html, inTable);
				if (trimmed.startsWith("#")) {
					closeParagraph(html, paragraph);
					closeLists(html, inUl, inOl);
					inUl = false;
					inOl = false;
					int level = headingLevel(trimmed);
					if (level > 0) {
						String text = trimmed.substring(level).trim();
						String anchor = anchor(text);
						level = Math.min(level, 3);
						html.append("<h").append(level).append("><a name=\"")
							.append(escapeAttribute(anchor))
							.append("\"></a>")
							.append(inline(text))
							.append("</h").append(level).append(">\n");
						continue;
					}
				}
				if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
					closeParagraph(html, paragraph);
					if (inOl) {
						html.append("</ol>\n");
						inOl = false;
					}
					if (!inUl) {
						html.append("<ul>\n");
						inUl = true;
					}
					html.append("<li>").append(inline(trimmed.substring(2).trim())).append("</li>\n");
					continue;
				}
				int orderedOffset = orderedListOffset(trimmed);
				if (orderedOffset > 0) {
					closeParagraph(html, paragraph);
					if (inUl) {
						html.append("</ul>\n");
						inUl = false;
					}
					if (!inOl) {
						html.append("<ol>\n");
						inOl = true;
					}
					html.append("<li>").append(inline(trimmed.substring(orderedOffset).trim())).append("</li>\n");
					continue;
				}
				closeLists(html, inUl, inOl);
				inUl = false;
				inOl = false;
				appendParagraphText(paragraph, trimmed);
			}
			closeParagraph(html, paragraph);
			closeLists(html, inUl, inOl);
			closeTable(html, inTable);
			if (inCode) {
				html.append("</code></pre>\n");
			}
			return html.toString();
		}

		private static void appendParagraphText(StringBuilder paragraph, String text) {
			if (paragraph.length() > 0) {
				paragraph.append(' ');
			}
			paragraph.append(text);
		}

		private static void closeParagraph(StringBuilder html, StringBuilder paragraph) {
			if (paragraph.length() == 0) {
				return;
			}
			html.append("<p>").append(inline(paragraph.toString())).append("</p>\n");
			paragraph.setLength(0);
		}

		private static void closeLists(StringBuilder html, boolean inUl, boolean inOl) {
			if (inUl) {
				html.append("</ul>\n");
			}
			if (inOl) {
				html.append("</ol>\n");
			}
		}

		private static boolean closeTable(StringBuilder html, boolean inTable) {
			if (inTable) {
				html.append("</table>\n");
			}
			return false;
		}

		private static int headingLevel(String text) {
			int level = 0;
			while (level < text.length() && text.charAt(level) == '#') {
				level++;
			}
			return level > 0 && level < text.length() && Character.isWhitespace(text.charAt(level)) ? level : 0;
		}

		private static String anchor(String text) {
			StringBuilder anchor = new StringBuilder();
			boolean dash = false;
			for (int index = 0; index < text.length(); index++) {
				char ch = Character.toLowerCase(text.charAt(index));
				if (Character.isLetterOrDigit(ch)) {
					anchor.append(ch);
					dash = false;
				} else if (!dash && anchor.length() > 0) {
					anchor.append('-');
					dash = true;
				}
			}
			while (anchor.length() > 0 && anchor.charAt(anchor.length() - 1) == '-') {
				anchor.setLength(anchor.length() - 1);
			}
			return anchor.length() == 0 ? "section" : anchor.toString();
		}

		private static int orderedListOffset(String text) {
			int index = 0;
			while (index < text.length() && Character.isDigit(text.charAt(index))) {
				index++;
			}
			if (index == 0 || index + 1 >= text.length()) {
				return -1;
			}
			return text.charAt(index) == '.' && Character.isWhitespace(text.charAt(index + 1)) ? index + 2 : -1;
		}

		private static boolean isTableRow(String text) {
			return text.startsWith("|") && text.endsWith("|");
		}

		private static boolean isTableSeparator(String text) {
			if (!isTableRow(text)) {
				return false;
			}
			String compact = text.replace("|", "").replace(":", "").replace("-", "").trim();
			return compact.isEmpty();
		}

		private static void appendTableRow(StringBuilder html, String row, boolean header) {
			html.append("<tr>");
			String[] cells = row.substring(1, row.length() - 1).split("\\|", -1);
			String tag = header ? "th" : "td";
			for (String cell : cells) {
				html.append('<').append(tag).append('>')
					.append(inline(cell.trim()))
					.append("</").append(tag).append('>');
			}
			html.append("</tr>\n");
		}

		private static String inline(String text) {
			StringBuilder html = new StringBuilder();
			for (int index = 0; index < text.length(); index++) {
				char ch = text.charAt(index);
				if (ch == '`') {
					int end = text.indexOf('`', index + 1);
					if (end > index) {
						html.append("<code>").append(escape(text.substring(index + 1, end))).append("</code>");
						index = end;
						continue;
					}
				}
				if (ch == '[') {
					int labelEnd = text.indexOf(']', index + 1);
					if (labelEnd > index && labelEnd + 1 < text.length() && text.charAt(labelEnd + 1) == '(') {
						int hrefEnd = text.indexOf(')', labelEnd + 2);
						if (hrefEnd > labelEnd) {
							String label = text.substring(index + 1, labelEnd);
							String href = text.substring(labelEnd + 2, hrefEnd);
							html.append("<a href=\"").append(escapeAttribute(href)).append("\">")
								.append(escape(label))
								.append("</a>");
							index = hrefEnd;
							continue;
						}
					}
				}
				html.append(escapeChar(ch));
			}
			return html.toString();
		}

		private static String escape(String value) {
			StringBuilder escaped = new StringBuilder();
			for (int index = 0; index < value.length(); index++) {
				escaped.append(escapeChar(value.charAt(index)));
			}
			return escaped.toString();
		}

		private static String escapeAttribute(String value) {
			return escape(value).replace("\"", "&quot;");
		}

		private static String escapeChar(char ch) {
			return switch (ch) {
				case '<' -> "&lt;";
				case '>' -> "&gt;";
				case '&' -> "&amp;";
				case '"' -> "&quot;";
				default -> String.valueOf(ch);
			};
		}
	}
}
