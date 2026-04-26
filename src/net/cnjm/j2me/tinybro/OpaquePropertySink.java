package net.cnjm.j2me.tinybro;

/**
 * Optional back-reference for native-backed {@link Rv} objects: when a property
 * is written on the object, the opaque payload can mirror the value without a
 * second {@link Rhash} lookup in hot native methods (e.g. {@code Image.draw}).
 */
public interface OpaquePropertySink {
    void onPropertyPut(String key, Rv value);
}
