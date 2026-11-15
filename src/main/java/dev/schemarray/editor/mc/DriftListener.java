package dev.schemarray.editor.mc;

import dev.schemarray.editor.core.Node;
import dev.schemarray.editor.core.ObjectNode;

/** Told by the reconciler about changes made outside the editor. The undo stack (WP 1.2) implements it. */
public interface DriftListener {
    DriftListener NONE = new DriftListener() {
        @Override
        public void onExternalChange(ExternalChange change) {}
    };

    /** A base placement drifted and the model took it over. */
    void onExternalChange(ExternalChange change);

    /**
     * The placement of a Loose object was deleted in Litematica, so the object was removed from Loose too.
     * ({@code parent} and {@code index} say where it was.)
     */
    default void onExternalRemoved(ObjectNode node, Node parent, int index) {}
}
