package com.pojosontheweb.selenium;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.openqa.selenium.WebDriver;

/**
 * Base JUnit4 test class.
 */
public class ManagedDriverJunit4TestBase {

    public static final String PROP_WEBTESTS_FINDR_TIMEOUT = "webtests.findr.timeout";
    public static final String PROP_WEBTESTS_FINDR_SLEEP = "webtests.findr.sleep";

    private final TestUtil testUtil = new TestUtil();

    private static String toTestName(Description d) {
        return d.getClassName() + "." + d.getMethodName();
    }

    @Rule
    public TestRule watchman = new TestWatcher() {

        @Override
        public Statement apply(Statement base, Description description) {
            return super.apply(base, description);
        }

        @Override
        protected void succeeded(Description description) {
            if (testUtil.isFailuresOnly()) {
                testUtil.removeVideoFiles();
            } else {
                testUtil.moveVideoFiles(toTestName(description));
            }
        }

        @Override
        protected void failed(Throwable e, Description description) {
            // keep video for failed tests if video is on
            testUtil.moveVideoFiles(toTestName(description));
        }

        @Override
        protected void starting(Description description) {
            testUtil.setUp();
        }

        @Override
        protected void finished(Description description) {
            testUtil.tearDown();
        }
    };

    protected final WebDriver getWebDriver() {
        return testUtil.getWebDriver();
    }

    protected Findr findr() {
        Findr f = new Findr(getWebDriver());
        String timeoutStr = System.getProperty(PROP_WEBTESTS_FINDR_TIMEOUT);
        if (timeoutStr!=null) {
            f = f.setTimeout(Integer.parseInt(timeoutStr));
        }
        String sleepInMillisStr = System.getProperty(PROP_WEBTESTS_FINDR_SLEEP);
        if (sleepInMillisStr!=null) {
            f = f.setSleepInMillis(Long.parseLong(sleepInMillisStr));
        }
        return f;
    }

    /**
     * @deprecated use <code>findr().setTimeout(t)</code> instead.
     */
    @Deprecated
    protected Findr findr(int timeoutInSeconds) {
        return new Findr(getWebDriver(), timeoutInSeconds);
    }

}
