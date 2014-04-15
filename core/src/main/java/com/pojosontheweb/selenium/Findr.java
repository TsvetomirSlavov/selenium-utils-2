package com.pojosontheweb.selenium;

import java.util.*;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class Findr {

    private static final int WAIT_TIMEOUT = 10; // secs

    private final WebDriver driver;
    private final Function<SearchContext,WebElement> f;
    private final List<String> path;
    private final int waitTimeout;
    private final boolean strictMode; // will throw if one tries to return a WebElem from a callback Function

    public Findr(WebDriver driver) {
        this(driver,WAIT_TIMEOUT);
    }

    public Findr(WebDriver driver, int waitTimeout) {
        this(driver, waitTimeout, null, Collections.<String>emptyList());
    }

    public static Findr toStrict(Findr f) {
        return new Findr(f.driver, f.waitTimeout, f.f, f.path, true);
    }

    private Findr(WebDriver driver, int waitTimeout, Function<SearchContext, WebElement> f, List<String> path) {
        this(driver, waitTimeout, f, path, false);
    }

    private Findr(WebDriver driver, int waitTimeout, Function<SearchContext, WebElement> f, List<String> path, boolean strictMode) {
        this.driver = driver;
        this.waitTimeout = waitTimeout;
        this.f = f;
        this.path = path;
        this.strictMode = strictMode;
    }

    private <F,T> Function<F,T> wrapAndTrapCatchStaleElementException(final Function<F,T> function) {
        return new Function<F,T>() {
            @Override
            public T apply(F input) {
                try {
                    return function.apply(input);
                } catch(StaleElementReferenceException e) {
                    return null;
                }
            }
        };
    }

    private Findr compose(Function<SearchContext,WebElement> function, String pathElem) {
        Function<SearchContext,WebElement> newFunction = wrapAndTrapCatchStaleElementException(function);
        ArrayList<String> newPath = new ArrayList<String>(path);
        if (pathElem!=null) {
            newPath.add(pathElem);
        }
        if (f==null) {
            return new Findr(driver, waitTimeout, newFunction, newPath, strictMode);
        } else {
            return new Findr(driver, waitTimeout, Functions.compose(newFunction, f), newPath, strictMode);
        }
    }

    /**
     * Adds an element selector to the path
     * @param by the locator
     * @return a new updated Finder
     */
    public Findr elem(final By by) {
        return compose(
                new Function<SearchContext, WebElement>() {
                    @Override
                    public WebElement apply(SearchContext input) {
                        if (input==null) {
                            return null;
                        }
                        try {
                            return input.findElement(by);
                        } catch(Exception e) {
                            return null;
                        }
                    }
                },
                by.toString()
        );
    }

    /**
     * Adds a list selector to the path
     * @param by the locator
     * @return a new ListFinder
     */
    public ListFindr elemList(By by) {
        return new ListFindr(by);
    }

    private <T> T wrapWebDriverWait(final Function<WebDriver,T> callback) throws TimeoutException {
        try {
            return new WebDriverWait(driver, waitTimeout).until(callback);
        } catch(TimeoutException e) {
            // failed to find element(s), build exception message
            // and re-throw exception
            StringBuilder sb = new StringBuilder();
            for (Iterator<String> it = path.iterator(); it.hasNext(); ) {
                sb.append(it.next());
                if (it.hasNext()) {
                    sb.append("->");
                }
            }
            throw new TimeoutException("Timed out trying to find path=" + sb.toString() + ", callback=" + callback, e);
        }
    }

    /**
     * Call all the selectors in the path. If they all succeed before timeout,
     * then delegate processing to <code>callback</code>, passing the retrieved WebElement.
     * @param callback the callback to be invoked when all previous elements have been retrieved
     * @param <T> the return type of the callback
     * @return the callback's result if all elements in the chain have been retrieved
     * @throws TimeoutException if one of the element(s) in the chain could not be retrieved
     */
    public <T> T eval(final Function<WebElement,T> callback) throws TimeoutException {
        return wrapWebDriverWait(wrapAndTrapCatchStaleElementException(new Function<WebDriver, T>() {
            @Override
            public T apply(WebDriver input) {
                WebElement e = f.apply(input);
                if (e == null) {
                    return null;
                }
                return strictApply(callback, e);
            }
        }));
    }

    private <R,I> R strictApply(Function<I,R> callback, I arg) {
        R result = callback.apply(arg);
        if (strictMode && result instanceof WebElement) {
            throw new RuntimeException("Suspicious code ! Trying to return a WebElement from a Finder callback.\n" +
                    "This is considered bad practice as it can lead to StaleElementExceptions. Instead, prefer using the WebElement " +
                    "inside the callback only.\nCallback : " + callback.toString());
        }
        return result;
    }

    private static final Function<WebElement,?> IDENTITY_FOR_EVAL = new Function<WebElement, Object>() {
        @Override
        public Object apply(WebElement webElement) {
            return true;
        }
    };

    /**
     * Evaluates the whole condition chain, and returns if everything went ok. Throws TimeoutException if condition(s) fail.
     * @throws TimeoutException if one of the conditions in the chain fails.
     */
    public void eval() throws TimeoutException {
        eval(IDENTITY_FOR_EVAL);
    }

    public void eval(String failureMessage) throws TimeoutException {
        try {
            eval();
        } catch(TimeoutException e) {
            throw new TimeoutException(failureMessage, e);
        }
    }


    public <T> T eval(final Function<WebElement,T> callback, String failureMessage) {
        try {
            return eval(callback);
        } catch (TimeoutException e) {
            throw new TimeoutException(failureMessage, e);
        }
    }

    /**
     * Adds a predicate for matching a WebElement to the search function
     * @param predicate the predicate
     * @return a new updated Finder
     */
    public Findr where(final Predicate<? super WebElement> predicate) {
        return compose(new Function<SearchContext, WebElement>() {
            @Override
            public WebElement apply(SearchContext input) {
                if (input==null) {
                    return null;
                }
                if (input instanceof WebElement) {
                    WebElement webElement = (WebElement)input;
                    if (predicate.apply(webElement)) {
                        return webElement;
                    }
                    return null;
                } else {
                    throw new RuntimeException("input is not a WebElement : " + input);
                }
            }
        },
                predicate.toString()
        );
    }

    private static final Predicate<WebElement> TRUE = com.google.common.base.Predicates.alwaysTrue();

    public void sendKeys(final CharSequence... keys) {
        eval(new Function<WebElement, Object>() {
            @Override
            public Object apply(WebElement webElement) {
                webElement.sendKeys(keys);
                return true;
            }

            @Override
            public String toString() {
                return "sendKeys(" + Arrays.toString(keys) + ")";
            }
        });
    }

    public void click() {
        eval(new Function<WebElement, Object>() {
            @Override
            public Object apply(WebElement webElement) {
                webElement.click();
                return true;
            }

            @Override
            public String toString() {
                return "click()";
            }
        });
    }

    public void clear() {
        eval(new Function<WebElement, Object>() {
            @Override
            public Object apply(WebElement webElement) {
                webElement.clear();
                return true;
            }

            @Override
            public String toString() {
                return "clear()";
            }
        });
    }

    private static final Function<List<WebElement>,Object> IDENTITY_LIST = new Function<List<WebElement>, Object>() {
        @Override
        public Object apply(List<WebElement> webElements) {
            return webElements;
        }
    };

    public class ListFindr {

        private final By by;
        private final Predicate<WebElement> filters;
        private final Integer waitCount;

        private ListFindr(By by) {
            this(by, TRUE, null);
        }

        private ListFindr(By by, Predicate<WebElement> filters, Integer waitCount) {
            this.by = by;
            this.filters = filters;
            this.waitCount = waitCount;
        }

        private Predicate<WebElement> wrapAndTrap(final Predicate<? super WebElement> predicate) {
            return new Predicate<WebElement>() {
                @Override
                public boolean apply(WebElement input) {
                    if (input==null) {
                        return false;
                    }
                    try {
                        return predicate.apply(input);
                    } catch(StaleElementReferenceException e) {
                        return false;
                    }

                }
            };
        }

        private <T> T wrapWebDriverWaitList(final Function<WebDriver,T> callback) throws TimeoutException {
            try {
                return new WebDriverWait(driver, waitTimeout).until(callback);
            } catch(TimeoutException e) {
                // failed to find element(s), build exception message
                // and re-throw exception
                ArrayList<String> newPath = new ArrayList<String>(path);
                newPath.add(by.toString());
                StringBuilder sb = new StringBuilder();
                for (Iterator<String> it = newPath.iterator(); it.hasNext(); ) {
                    sb.append(it.next());
                    if (it.hasNext()) {
                        sb.append("->");
                    }
                }
                throw new TimeoutException("Timed out trying to find path=" + sb.toString() + ", callback=" + callback, e);
            }
        }


        /**
         * Filter the list of elements using passed predicate
         * @param predicate a predicate for matching filtered WebElements
         * @return a new updated ListFinder
         */
        public ListFindr where(final Predicate<? super WebElement> predicate) {
            return new ListFindr(by, com.google.common.base.Predicates.<WebElement>and(filters, wrapAndTrap(predicate)), waitCount);
        }

        /**
         * Select an element in a list of elements
         * @param index the index of the element in the list
         * @return a new updated Finder
         */
        public Findr at(final int index) {
            return compose(new Function<SearchContext, WebElement>(){
                @Override
                public WebElement apply(SearchContext input) {
                    List<WebElement> elements;
                    try {
                        elements = filterElements(input.findElements(by));
                    } catch(Exception e) {
                        return null;
                    }
                    if (elements==null) {
                        return null;
                    }
                    if (index>=elements.size()) {
                        return null;
                    }
                    return elements.get(index);
                }
            },
                    by.toString() + "[" + index + "]"
            );
        }

        private List<WebElement> filterElements(List<WebElement> source) {
            List<WebElement> filtered = new ArrayList<WebElement>();
            for (WebElement element : source) {
                if (filters.apply(element)) {
                    filtered.add(element);
                }
            }
            return filtered;
        }

        public ListFindr whereElemCount(int elemCount) {
            return new ListFindr(by, filters, elemCount);
        }

        /**
         * Call all the selectors in the path. If they all succeed before timeout,
         * then delegate processing to <code>callback</code>, passing the retrieved list of WebElements.
         * @param callback the callback to be invoked when all previous elements have been retrieved
         * @param <T> the return type of the callback
         * @return the callback's result if all elements in the chain have been retrieved
         * @throws TimeoutException if one of the element(s) in the chain could not be retrieved
         */
        public <T> T eval(final Function<List<WebElement>, T> callback) {
            return wrapWebDriverWaitList(wrapAndTrapCatchStaleElementException(new Function<WebDriver, T>() {
                @Override
                public T apply(WebDriver input) {
                    SearchContext c = f==null ? input : f.apply(input);
                    if (c == null) {
                        return null;
                    }
                    List<WebElement> elements = c.findElements(by);
                    if (elements == null) {
                        return null;
                    }
                    if (waitCount!=null && elements.size()!=waitCount) {
                        return null;
                    }
                    return strictApply(callback, filterElements(elements));
                }
            }));
        }

        public void eval() {
            eval(IDENTITY_LIST);
        }

        public void eval(String failureMessage) {
            try {
                eval(IDENTITY_LIST);
            } catch(TimeoutException e) {
                throw new TimeoutException(failureMessage, e);
            }
        }

    }

    // Utility statics
    public static Predicate<WebElement> attrEquals(final String attrName, final String expectedValue) {
        return new Predicate<WebElement>() {
            @Override
            public boolean apply(WebElement webElement) {
                String attrVal = webElement.getAttribute(attrName);
                return attrVal!=null && attrVal.equals(expectedValue);
            }

            @Override
            public String toString() {
                return "attrEquals(" + attrName + "," + expectedValue + ")";
            }
        };
    }

    public static Predicate<WebElement> hasClass(final String className) {
        return new Predicate<WebElement>() {
            @Override
            public boolean apply(WebElement webElement) {
                String cssClasses = webElement.getAttribute("class");
                // TODO needs tokenize (substring ain't enough)
                return cssClasses!=null && cssClasses.contains(className);
            }

            @Override
            public String toString() {
                return "hasClass(" + className + ")";
            }
        };
    }


    public static Predicate<WebElement> textEquals(final String expected) {
        return new Predicate<WebElement>() {
            @Override
            public boolean apply(WebElement webElement) {
                String text = webElement.getText();
                return text!=null && text.equals(expected);
            }

            @Override
            public String toString() {
                return "textEquals(" + expected + ")";
            }
        };
    }


    public static Predicate<WebElement> isEnabled() {
        return new Predicate<WebElement>() {
            @Override
            public boolean apply(WebElement input) {
                return input.isEnabled();
            }

            @Override
            public String toString() {
                return "isEnabled";
            }
        };
    }

    public static Predicate<WebElement> isDisplayed() {
        return new Predicate<WebElement>() {
            @Override
            public boolean apply(WebElement input) {
                return input.isDisplayed();
            }

            @Override
            public String toString() {
                return "isDisplayed";
            }
        };
    }

    public static Predicate<WebElement> cssValue(final String propName, final String expectedValue) {
        return new Predicate<WebElement>() {
            @Override
            public boolean apply(WebElement webElement) {
                String attrVal = webElement.getCssValue(propName);
                return attrVal!=null && attrVal.equals(expectedValue);
            }

            @Override
            public String toString() {
                return "cssValue(" + propName + "," + expectedValue + ")";
            }
        };
    }

}