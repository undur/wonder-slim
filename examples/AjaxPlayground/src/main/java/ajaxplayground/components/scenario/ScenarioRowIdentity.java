package ajaxplayground.components.scenario;

import java.util.ArrayList;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: row identity under morph (the "data jumps lines" class).
 *
 * A list of rows lives inside a morphing container. Each row's container id can be either INDEX-based
 * ("row" + loop index) or IDENTITY-based (a stable per-row key). Deleting a middle row shifts every
 * row below it up by one. With INDEX-based ids, Idiomorph matches the OLD row-N node to the NEW
 * row-N content - which is now a DIFFERENT row's data - so it morphs the wrong preserved node, and
 * an in-flight edit / focus can end up on the wrong line. With IDENTITY-based ids each row's node
 * follows its row, so delete reconciles correctly.
 *
 * The page renders BOTH columns side by side from the same data so a single delete can be compared.
 */
public class ScenarioRowIdentity extends PlaygroundPage {

	/** A row with a stable identity (never reused) and an editable note we can track across morphs. */
	public static class Row {
		public final int id;     // stable identity, assigned once, never reused
		public String label;     // server-rendered label (changes as rows shift)
		public String note;      // editable - lets us see if an edit follows the right row

		Row( int id, String label ) {
			this.id = id;
			this.label = label;
		}
	}

	private List<Row> _rows;
	private int _nextId = 1;

	public Row currentRow;
	public int currentIndex;

	public ScenarioRowIdentity( WOContext context ) {
		super( context );
		_rows = new ArrayList<>();
		for( int i = 0; i < 6; i++ ) {
			addRow();
		}
	}

	private void addRow() {
		int id = _nextId++;
		Row r = new Row( id, "Row#" + id );
		_rows.add( r );
		relabel();
	}

	/** Labels reflect position, so they change when rows shift - exposing any node/data mismatch. */
	private void relabel() {
		for( int i = 0; i < _rows.size(); i++ ) {
			_rows.get( i ).label = "pos" + (i + 1) + " (id=" + _rows.get( i ).id + ")";
		}
	}

	public List<Row> rows() {
		return _rows;
	}

	// --- INDEX-based ids (the bug pattern) ---
	public String indexRowID() {
		return "irow" + currentIndex;
	}

	public String indexNoteFieldID() {
		return "inote" + currentIndex;
	}

	// --- IDENTITY-based ids (the fix) ---
	public String idRowID() {
		return "idrow" + currentRow.id;
	}

	public String idNoteFieldID() {
		return "idnote" + currentRow.id;
	}

	public String currentNote() {
		return currentRow.note;
	}

	public void setCurrentNote( String value ) {
		currentRow.note = value;
	}

	public WOActionResults deleteRow() {
		_rows.remove( currentRow );
		relabel();
		return null;
	}

	public WOActionResults noteChanged() {
		return null;
	}

	/** Trivial content for the echo containers the note observe-fields target. */
	public String noteEcho() {
		return "";
	}
}
