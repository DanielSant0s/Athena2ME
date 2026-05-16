package net.cnjm.j2me.tinybro;

/**
 * Optional ES6 preprocessor hooks loaded reflectively from {@link Es6PreprocFacade}.
 * Ship empty by default; advanced passes can be added without growing the core MIDlet.
 */
public final class Es6PreprocOptionalHooks {

    private Es6PreprocOptionalHooks() {
    }

    /** Called once when the optional hooks class is first resolved. */
    public static void touch() {
    }
}
