/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */
package rx.debug;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import rx.Observable;
import rx.functions.Func1;
import rx.observers.Subscribers;
import rx.plugins.DebugHook;
import rx.plugins.DebugNotification.Kind;
import rx.plugins.PlugReset;
import rx.plugins.RxJavaPlugins;
import rx.plugins.SimpleContext;
import rx.plugins.SimpleDebugNotificationListener;
import rx.plugins.SimpleDebugNotificationListener.NotificationsByObservable;

import java.util.Arrays;
import java.util.Iterator;
import java.util.SortedSet;

public class DebugHookTest {
    @Before
    public void reset() {
        System.out.println("#########reset");
        PlugReset.reset();
    }

    @Test
    public void testSimple() {
        SimpleDebugNotificationListener listener = new SimpleDebugNotificationListener();

        final DebugHook<SimpleContext<?>> hook = new DebugHook<SimpleContext<?>>(listener);
        RxJavaPlugins.getInstance().registerObservableExecutionHook(hook);

        Observable.from(Arrays.asList(1)).subscribe(Subscribers.empty());

        assertValidState(listener);
    }

    @Test
    public void testOneOp() {
        SimpleDebugNotificationListener listener = new SimpleDebugNotificationListener();

        // create and register the hooks.
        final DebugHook<SimpleContext<?>> hook = new DebugHook<SimpleContext<?>>(listener);
        RxJavaPlugins.getInstance().registerObservableExecutionHook(hook);

        // do the operation
        Observable.from(Arrays.asList(1, 3)).flatMap(new Func1<Integer, Observable<Integer>>() {
            @Override
            public Observable<Integer> call(Integer it) {
                return Observable.from(Arrays.asList(it * 10, (it + 1) * 10));
            }
        }).take(3).subscribe(Subscribers.<Integer> empty());

        assertValidState(listener);
    }

    public void assertValidState(SimpleDebugNotificationListener listener) {
        SortedSet<NotificationsByObservable<?>> snapshot = listener.getNotificationsByObservable();
        System.out.println(listener.toString(snapshot));
        for (NotificationsByObservable<?> notificationsForObserver : snapshot) {
            SortedSet<?> notificationsSet = notificationsForObserver.getNotifications();
            Iterator<SimpleContext<?>> notifications = (Iterator<SimpleContext<?>>) notificationsSet.iterator();

            if (!notifications.hasNext()) {
                fail("subscriber wasn't used.");
            }
            long allow = 0;
            SimpleContext<?> context = notifications.next();
            assertTrue("The first notification should be the subscribe or onStart", DebugHookTest.subscribe.matches(context) || DebugHookTest.onStart.matches(context));
            while (notifications.hasNext()) {
                context = notifications.next();
                if (DebugHookTest.onNext.matches(context)) {
                    assertTrue("request count not greater than zero", allow > 0);
                    allow--;
                } else if (DebugHookTest.request.matches(context)) {
                    allow += context.getNotification().getN();
                } else if (DebugHookTest.onCompleted.matches(context) || DebugHookTest.onError.matches(context) || DebugHookTest.unsubscribe.matches(context)) {
                    break;
                }
            }
            // if the last event was an unsubscribe then there should be not more events.
            if (DebugHookTest.unsubscribe.matches(context)) {
                assertFalse(notifications.hasNext());
            } else {
                // if it ended in an onComplete or onError there should be one and only one unsbuscribe
                assertTrue(notifications.hasNext());
                context = notifications.next();
                assertTrue("The last notification should be the unsubscribe", DebugHookTest.unsubscribe.matches(context));
                assertFalse(notifications.hasNext());
            }
        }
    }

    static Matcher<SimpleContext<Object>> subscribe = matcher(Kind.Subscribe);

    static Matcher<SimpleContext<Object>> onStart = matcher(Kind.OnStart);

    static Matcher<SimpleContext<Object>> request = matcher(Kind.Request);

    static Matcher<SimpleContext<Object>> onNext = matcher(Kind.OnNext);

    static Matcher<SimpleContext<Object>> onCompleted = matcher(Kind.OnCompleted);

    static Matcher<SimpleContext<Object>> onError = matcher(Kind.OnError);

    static Matcher<SimpleContext<Object>> unsubscribe = matcher(Kind.Unsubscribe);

    private static <T> BaseMatcher<SimpleContext<T>> matcher(final Kind kind) {
        return new BaseMatcher<SimpleContext<T>>() {
            @Override
            public boolean matches(Object item) {
                if (item instanceof SimpleContext) {
                    SimpleContext<?> context = (SimpleContext<?>) item;
                    return context.getNotification().getKind() == kind;
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(kind.name());
            }
        };
    }
}
