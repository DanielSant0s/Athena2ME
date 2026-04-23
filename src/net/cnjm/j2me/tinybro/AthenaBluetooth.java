package net.cnjm.j2me.tinybro;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;

/**
 * JSR-82 Bluetooth: device inquiry and capability probe. Callbacks from the
 * Bluetooth stack must only enqueue {@link PromiseRuntime.Microtask}s; never
 * call the JS interpreter directly.
 * <p>
 * <b>Build:</b> add {@code jsr082.jar} (or the OEM JSR-82 API jar) to the
 * compile classpath, e.g. from Sun Java ME SDK / WTK {@code lib/jsr082.jar}.
 */
public final class AthenaBluetooth {

    private static final Object INQUIRY_LOCK = new Object();
    private static boolean inquiryBusy;

    private static int bluetoothInFlight;

    static synchronized void btOpBegin() {
        bluetoothInFlight++;
    }

    static synchronized void btOpEnd() {
        if (bluetoothInFlight > 0) {
            bluetoothInFlight--;
        }
    }

    public static synchronized int getBluetoothInFlight() {
        return bluetoothInFlight;
    }

    private AthenaBluetooth() {}

    private static final class Discovered {
        final RemoteDevice device;
        final DeviceClass deviceClass;

        Discovered(RemoteDevice d, DeviceClass c) {
            device = d;
            deviceClass = c;
        }
    }

    private static final class InquiryListen implements DiscoveryListener {
        final RocksInterpreter ri;
        final Rv promise;
        final Vector discovered = new Vector();
        final Hashtable seen = new Hashtable();
        boolean settled;
        Timer timer;

        InquiryListen(RocksInterpreter ri, Rv promise) {
            this.ri = ri;
            this.promise = promise;
        }

        void settleReject(final String msg) {
            synchronized (this) {
                if (settled) {
                    return;
                }
                settled = true;
            }
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            final String m = msg != null && msg.length() > 0 ? msg : "Bluetooth inquiry failed";
            PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                public void run(RocksInterpreter ri2) {
                    RocksInterpreter use = ri2 != null ? ri2 : InquiryListen.this.ri;
                    synchronized (INQUIRY_LOCK) {
                        inquiryBusy = false;
                    }
                    btOpEnd();
                    PromiseRuntime.reject(use, promise, Rv.error(m));
                }
            });
        }

        public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {
            if (remoteDevice == null) {
                return;
            }
            try {
                String addr = remoteDevice.getBluetoothAddress();
                if (addr == null || seen.containsKey(addr)) {
                    return;
                }
                seen.put(addr, addr);
                discovered.addElement(new Discovered(remoteDevice, deviceClass));
            } catch (Throwable ignored) {
            }
        }

        public void inquiryCompleted(int discType) {
            synchronized (this) {
                if (settled) {
                    return;
                }
                settled = true;
            }
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            final Vector snap = new Vector();
            for (Enumeration e = discovered.elements(); e.hasMoreElements(); ) {
                snap.addElement(e.nextElement());
            }
            final int n = snap.size();
            final String[] addrs = new String[n];
            final String[] names = new String[n];
            final int[] majors = new int[n];
            for (int i = 0; i < n; i++) {
                Discovered d = (Discovered) snap.elementAt(i);
                addrs[i] = "";
                names[i] = "";
                majors[i] = 0;
                try {
                    if (d.device != null) {
                        addrs[i] = d.device.getBluetoothAddress();
                        if (addrs[i] == null) {
                            addrs[i] = "";
                        }
                    }
                } catch (Throwable ignored) {
                }
                try {
                    if (d.device != null) {
                        names[i] = d.device.getFriendlyName(false);
                        if (names[i] == null) {
                            names[i] = "";
                        }
                    }
                } catch (IOException ignored) {
                }
                try {
                    if (d.deviceClass != null) {
                        majors[i] = d.deviceClass.getMajorDeviceClass();
                    }
                } catch (Throwable ignored) {
                }
            }
            PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                public void run(RocksInterpreter ri2) {
                    RocksInterpreter use = ri2 != null ? ri2 : InquiryListen.this.ri;
                    synchronized (INQUIRY_LOCK) {
                        inquiryBusy = false;
                    }
                    btOpEnd();
                    Rv arr = use.newEmptyArray();
                    for (int i = 0; i < n; i++) {
                        Rv el = use.newModule();
                        use.addToObject(el, "address", new Rv(addrs[i]));
                        use.addToObject(el, "friendlyName", new Rv(names[i]));
                        use.addToObject(el, "majorDeviceClass", new Rv(majors[i]));
                        StdLib.pushItem(arr, el);
                    }
                    PromiseRuntime.fulfill(use, promise, arr);
                }
            });
        }

        public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
        }

        public void serviceSearchCompleted(int transID, int respCode) {
        }
    }

    /**
     * Synchronous snapshot: {@code available}, {@code jsr82}, {@code powered},
     * {@code name}, {@code address}, {@code error}.
     */
    public static Rv getCapabilities(RocksInterpreter ri) {
        Rv o = ri.newModule();
        ri.addToObject(o, "jsr82", new Rv(1));
        ri.addToObject(o, "available", new Rv(0));
        ri.addToObject(o, "powered", new Rv(0));
        ri.addToObject(o, "name", new Rv(""));
        ri.addToObject(o, "address", new Rv(""));
        ri.addToObject(o, "error", new Rv(""));
        try {
            LocalDevice ld = LocalDevice.getLocalDevice();
            ri.addToObject(o, "available", new Rv(1));
            ri.addToObject(o, "powered", new Rv(1));
            String addr = ld.getBluetoothAddress();
            if (addr != null) {
                ri.addToObject(o, "address", new Rv(addr));
            }
            try {
                String fn = ld.getFriendlyName();
                if (fn != null) {
                    ri.addToObject(o, "name", new Rv(fn));
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            String m = t.getMessage() != null ? t.getMessage() : t.toString();
            ri.addToObject(o, "available", new Rv(0));
            ri.addToObject(o, "powered", new Rv(0));
            ri.addToObject(o, "error", new Rv(m));
        }
        return o;
    }

    /**
     * Device inquiry; fulfills with a dense JS array of
     * {@code { address, friendlyName, majorDeviceClass }}.
     */
    public static Rv inquiryPromise(final RocksInterpreter ri, int timeoutMs) {
        if (ri == null) {
            return PromiseRuntime.rejected(null, Rv.error("no interpreter"));
        }
        if (timeoutMs <= 0) {
            timeoutMs = 30000;
        }
        synchronized (INQUIRY_LOCK) {
            if (inquiryBusy) {
                return PromiseRuntime.rejected(ri, Rv.error("Bluetooth inquiry busy"));
            }
            inquiryBusy = true;
        }
        btOpBegin();
        final Rv promise = PromiseRuntime.createPending(ri);
        final int timeout = timeoutMs;
        new Thread(new Runnable() {
            public void run() {
                final InquiryListen listener = new InquiryListen(ri, promise);
                try {
                    LocalDevice local = LocalDevice.getLocalDevice();
                    final DiscoveryAgent agent = local.getDiscoveryAgent();
                    listener.timer = new Timer();
                    listener.timer.schedule(new TimerTask() {
                        public void run() {
                            try {
                                agent.cancelInquiry(listener);
                            } catch (Throwable ignored) {
                            }
                        }
                    }, (long) timeout);
                    boolean started = agent.startInquiry(DiscoveryAgent.GIAC, listener);
                    if (!started) {
                        listener.settleReject("startInquiry failed");
                    }
                } catch (Throwable t) {
                    listener.settleReject(t.getMessage() != null ? t.getMessage() : "inquiry error");
                }
            }
        }).start();
        return promise;
    }
}
