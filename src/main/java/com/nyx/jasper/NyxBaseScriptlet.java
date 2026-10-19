package com.nyx.jasper;

import net.sf.jasperreports.engine.JRDefaultScriptlet;
import net.sf.jasperreports.engine.JRScriptletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base scriptlet for all Nyx Jasper reports.
 *
 * Extend this class (or JRDefaultScriptlet directly) to add custom logic that
 * runs at specific points during report filling.  Override only the hooks you
 * need — every hook has a no-op default in JRDefaultScriptlet.
 *
 * Lifecycle order per page:
 *   beforeReportInit → beforePageInit → beforeColumnInit →
 *   beforeGroupInit  → afterGroupInit  →
 *   afterColumnInit  → afterPageInit   → afterReportInit
 *
 * Accessing report state inside any hook:
 *   getFieldValue("fieldName")       – current field value
 *   getVariableValue("varName")      – current variable value
 *   getParameterValue("paramName")   – parameter value
 *
 * Usage in JRXML:
 *   <jasperReport … scriptletClass="com.nyx.jasper.MyReportScriptlet">
 */
public abstract class NyxBaseScriptlet extends JRDefaultScriptlet {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // -------------------------------------------------------------------------
    // Report-level hooks
    // -------------------------------------------------------------------------

    @Override
    public void beforeReportInit() throws JRScriptletException {
        logger.debug("[scriptlet] beforeReportInit");
    }

    @Override
    public void afterReportInit() throws JRScriptletException {
        logger.debug("[scriptlet] afterReportInit");
    }

    // -------------------------------------------------------------------------
    // Page-level hooks
    // -------------------------------------------------------------------------

    @Override
    public void beforePageInit() throws JRScriptletException {
        logger.debug("[scriptlet] beforePageInit – page {}", getVariableValue("PAGE_NUMBER"));
    }

    @Override
    public void afterPageInit() throws JRScriptletException {
        logger.debug("[scriptlet] afterPageInit – page {}", getVariableValue("PAGE_NUMBER"));
    }

    // -------------------------------------------------------------------------
    // Column-level hooks
    // -------------------------------------------------------------------------

    @Override
    public void beforeColumnInit() throws JRScriptletException {
        logger.debug("[scriptlet] beforeColumnInit");
    }

    @Override
    public void afterColumnInit() throws JRScriptletException {
        logger.debug("[scriptlet] afterColumnInit");
    }

    // -------------------------------------------------------------------------
    // Group-level hooks  (override per group if needed)
    // -------------------------------------------------------------------------

    @Override
    public void beforeGroupInit(String groupName) throws JRScriptletException {
        logger.debug("[scriptlet] beforeGroupInit – group '{}'", groupName);
    }

    @Override
    public void afterGroupInit(String groupName) throws JRScriptletException {
        logger.debug("[scriptlet] afterGroupInit – group '{}'", groupName);
    }

    // -------------------------------------------------------------------------
    // Detail-band hook
    // -------------------------------------------------------------------------

    @Override
    public void beforeDetailEval() throws JRScriptletException {
        logger.debug("[scriptlet] beforeDetailEval");
    }

    @Override
    public void afterDetailEval() throws JRScriptletException {
        logger.debug("[scriptlet] afterDetailEval");
    }
}
