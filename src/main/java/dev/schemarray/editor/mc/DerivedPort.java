package dev.schemarray.editor.mc;

import dev.schemarray.editor.core.ObjectNode;

import java.nio.file.Path;

/** Which derived file (trims, Booleans) the base cell of an object uses right now; no Minecraft types. */
@FunctionalInterface
public interface DerivedPort {
    /** The ready derived file, or null to use the object own file. */
    Path fileFor(ObjectNode object);
}
