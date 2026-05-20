/**
 * Mini-browser demo: Java layout + JS paint (see /lib/browser_render.js).
 * Same integration as other demos: exports.start + exports.frame from main.js
 * single os.startFrameLoop (never nest a second startFrameLoop here).
 */

var render = require("/lib/browser_render.js");

var PAGE_A =
    "<div style=\"background:#0a1020;color:#e8f0ff;padding:8px;width:100%\">" +
    "<p style=\"font-size:large;font-weight:bold\">Page A</p>" +
    "<p>D-pad UP/DOWN scrolls the page. C/D moves focus. FIRE activates links.</p>" +
    "<p><a href=\"app://page-b\">Go to page B (app)</a></p>" +
    "<p><a href=\"https://example.com\">Load example.com (HTTP)</a></p>" +
    "<form action=\"app://echo\" method=\"get\">" +
    "<input type=\"text\" name=\"q\" value=\"demo\"/> " +
    "<input type=\"submit\" value=\"GET echo\"/>" +
    "</form>" +
    "<p><img src=\"/cube_texture.png\" style=\"width:48;height:48\"/></p>" +
    "</div>";

var PAGE_B =
    "<div style=\"background:#102018;color:#101010;padding:8px;width:100%\">" +
    "<p style=\"font-size:large\">Page B</p>" +
    "<p><a href=\"app://page-a\">Back to page A</a></p>" +
    "</div>";

var appPages = {
    "app://page-a": PAGE_A,
    "app://page-b": PAGE_B
};

var nav = null;

function appBase(url) {
    if (url.indexOf("app://") !== 0) {
        return url;
    }
    var qm = url.indexOf("?");
    if (qm >= 0) {
        return url.substring(0, qm);
    }
    return url;
}

function allowUrl(url) {
    if (url.indexOf("app://echo") === 0) {
        return true;
    }
    if (url.indexOf("app://") === 0) {
        return appPages[appBase(url)] != null;
    }
    if (url.indexOf("https://example.com") === 0) {
        return true;
    }
    if (url.indexOf("http://example.com") === 0) {
        return true;
    }
    return false;
}

function loadAppUrl(url) {
    var html = appPages[appBase(url)];
    if (html == null) {
        return;
    }
    Browser.loadHtml(html);
    Browser.historyPush(url);
}

function goHistoryUrl(url) {
    if (url.indexOf("app://") === 0) {
        if (url.indexOf("app://echo") === 0) {
            Browser.loadHtml(
                "<div style=\"padding:8px;color:#101010;background:#203040;width:100%\">" +
                "<p>Echo (GET)</p><p><a href=\"app://page-a\">OK</a></p></div>"
            );
            return;
        }
        var base = appBase(url);
        if (appPages[base] != null) {
            Browser.loadHtml(appPages[base]);
        }
        return;
    }
    if (url.indexOf("http://") === 0 || url.indexOf("https://") === 0) {
        render.loadHttpDocument(url, "GET", "", false);
    }
}

exports.start = function (back) {
    nav = {
        onBack: function () {
            Browser.clear();
            nav = null;
            back();
        },
        allowUrl: allowUrl,
        loadAppHtml: function (url) {
            if (url.indexOf("app://echo") === 0) {
                Browser.loadHtml(
                    "<div style=\"padding:8px;color:#101010;background:#203040;width:100%\">" +
                    "<p>Echo (GET)</p><p><a href=\"app://page-a\">OK</a></p></div>"
                );
                Browser.historyPush(url);
                return;
            }
            loadAppUrl(url);
        },
        loadHttp: function (url, method, body) {
            render.loadHttpDocument(url, method, body);
        },
        goHistoryUrl: goHistoryUrl
    };

    Browser.loadHtml(PAGE_A);
    Browser.historyPush("app://page-a");
};

exports.frame = function () {
    if (nav == null) {
        return;
    }
    render.frame(nav);
};
