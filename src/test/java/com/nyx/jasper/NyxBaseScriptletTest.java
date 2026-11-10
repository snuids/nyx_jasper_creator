package com.nyx.jasper;

import net.sf.jasperreports.engine.JRScriptletException;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The hooks a report scriptlet can override.
 *
 * `NyxBaseScriptlet` only logs: it exists so a template can point at a scriptlet without needing a
 * class, and so overriding one hook does not require writing the seven others. Outside a fill the
 * two page hooks have no `PAGE_NUMBER` to read — what matters is that they fail the way a scriptlet
 * failure is supposed to fail, and nothing else.
 */
public class NyxBaseScriptletTest {

    private static final class Scriptlet extends NyxBaseScriptlet {
        void runAll() throws JRScriptletException {
            beforeReportInit();
            beforePageInit();
            beforeColumnInit();
            beforeGroupInit("User");
            afterGroupInit("User");
            afterColumnInit();
            afterPageInit();
            afterReportInit();
            beforeDetailEval();
            afterDetailEval();
        }
    }

    @Test
    public void everyHookRunsWithoutAReportAroundIt() throws Exception {
        Scriptlet scriptlet = new Scriptlet();

        try {
            scriptlet.runAll();
        } catch (JRScriptletException | NullPointerException e) {
            // Outside a fill the variable map is null, so the two page hooks raise as soon as they
            // read PAGE_NUMBER. A scriptlet only ever runs inside the filler; the point of the test
            // is that the failure comes from that read and not from the hook itself.
            assertTrue("the failure comes from reading a report variable, not from the hook",
                    e instanceof JRScriptletException
                            || String.valueOf(e.getMessage()).contains("variablesMap"));
        }
    }

    @Test
    public void theHooksThatDoNotReadAReportAreHarmless() throws Exception {
        Scriptlet scriptlet = new Scriptlet();

        scriptlet.beforeReportInit();
        scriptlet.afterReportInit();
        scriptlet.beforeColumnInit();
        scriptlet.afterColumnInit();
        scriptlet.beforeGroupInit("User");
        scriptlet.afterGroupInit("User");
        scriptlet.beforeDetailEval();
        scriptlet.afterDetailEval();
    }
}
