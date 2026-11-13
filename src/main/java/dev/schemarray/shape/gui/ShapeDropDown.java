package dev.schemarray.shape.gui;

import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.interfaces.IStringRetriever;

import java.util.List;

/**
 * malilib's drop down list plus the two things the Shape Generator screen needs from it: asking whether the list is
 * open and closing it from outside (malilib only closes it on a click on the list itself, so a click anywhere else
 * would leave it open over the controls below).
 */
final class ShapeDropDown<T> extends WidgetDropDownList<T> {
    ShapeDropDown(int x, int y, int width, List<T> entries, IStringRetriever<T> names) {
        super(x, y, width, 20, 160, 8, entries, names);
    }

    boolean opened() {
        return isOpen;
    }

    void close() {
        if (!isOpen) return;
        isOpen = false;
        searchBar.textField().setValue("");
        updateFilteredEntries();
    }
}
