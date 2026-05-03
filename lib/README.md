# lib/ — J2ME API stubs

Compile-time only. These JARs are **not bundled** into the MIDlet; they are
only on the `javac` bootclasspath so the compiler knows the J2ME API surface.

Copy the following files from your local WTK 2.5.2 installation before building:

| File | Source | Description |
|------|--------|-------------|
| `cldcapi11.jar` | `C:\WTK2.5.2_01\lib\cldcapi11.jar` | CLDC 1.1 API |
| `midpapi20.jar` | `C:\WTK2.5.2_01\lib\midpapi20.jar` | MIDP 2.0 API |
| `mmapi.jar` | `C:\WTK2.5.2_01\lib\mmapi.jar` | Mobile Media API (JSR-135) |
| `jsr75.jar` | `C:\WTK2.5.2_01\lib\jsr75.jar` | FileConnection + PIM (JSR-75) |
| `jsr184.jar` | `C:\WTK2.5.2_01\lib\jsr184.jar` | Mobile 3D Graphics (JSR-184 / M3G) |
| `nokiaui.jar` | `C:\Users\danie\j2mewtk\2.5.2\lib\ext\nokiaui.jar` | Nokia UI API |
| `jsr82.jar` | `C:\WTK2.5.2_01\lib\jsr082.jar` | Bluetooth (JSR-82) — `javax.bluetooth.*` |
| `jsr177_crypto.jar` | `C:\WTK2.5.2_01\lib\satsa-crypto.jar` | SATSA Crypto (JSR-177) — `MessageDigest` etc. |

These JARs are freely distributed as part of the Sun/Oracle Wireless Toolkit
and the Nokia Developer Tools. They are committed here as compile-time stubs
for reproducible CI builds. No runtime code from these JARs is included in the
output MIDlet.
