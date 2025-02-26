package de.jeff_media.EventLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import com.google.common.collect.Lists;

public class CancellationDetector<TEvent extends Event> {
    interface CancelListener<TEvent extends Event> {
        public void onCancelled(Plugin plugin, TEvent event);
    }

    private final Class<TEvent> eventClazz;
    private final List<CancelListener<TEvent>> listeners = Lists.newArrayList();

    // For reverting the detector
    private EnumMap<EventPriority, ArrayList<RegisteredListener>> backup;

    public CancellationDetector(Class<TEvent> eventClazz) {
        this.eventClazz = eventClazz;
        injectProxy();
    }

    public void addListener(CancelListener<TEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(CancelListener<Event> listener) {
        listeners.remove(listener);
    }

    @SuppressWarnings("unchecked")
    private EnumMap<EventPriority, ArrayList<RegisteredListener>> getSlots(HandlerList list) {
        try {
            return (EnumMap<EventPriority, ArrayList<RegisteredListener>>) getSlotsField(list).get(list);
        } catch (Exception e) {
            throw new RuntimeException("Unable to retrieve slots.", e);
        }
    }

    private Field getSlotsField(HandlerList list) {
        if (list == null)
            throw new IllegalStateException("Detected a NULL handler list.");

        try {
            Field slotField = list.getClass().getDeclaredField("handlerslots");

            // Get our slot map
            slotField.setAccessible(true);
            return slotField;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to intercept 'handlerslot' in " + list.getClass(), e);
        }
    }

    private void injectProxy() {
        HandlerList list = getHandlerList(eventClazz);
        EnumMap<EventPriority, ArrayList<RegisteredListener>> slots = getSlots(list);

        // Keep a copy of this map
        backup = slots.clone();

        synchronized (list) {
            for (EventPriority p : slots.keySet().toArray(new EventPriority[0])) {
                final EventPriority priority = p;
                final ArrayList<RegisteredListener> proxyList = new ArrayList<RegisteredListener>() {
                    private static final long serialVersionUID = 7869505892922082581L;

                    @Override
                    public boolean add(RegisteredListener e) {
                        super.add(injectRegisteredListener(e));
                        return backup.get(priority).add(e);
                    }

                    @Override
                    public boolean remove(Object listener) {
                        // Remove this listener
                        for (Iterator<RegisteredListener> it = iterator(); it.hasNext(); ) {
                            DelegatedRegisteredListener delegated = (DelegatedRegisteredListener) it.next();
                            if (delegated.delegate == listener) {
                                it.remove();
                                break;
                            }
                        }
                        return backup.get(priority).remove(listener);
                    }
                };
                slots.put(priority, proxyList);

                for (RegisteredListener listener : backup.get(priority)) {
                    proxyList.add(listener);
                }
            }
        }
    }

    // The core of our magic
    private RegisteredListener injectRegisteredListener(final RegisteredListener listener) {
        return new DelegatedRegisteredListener(listener) {
            @SuppressWarnings("unchecked")
            @Override
            public void callEvent(Event event) throws EventException {
                if (event instanceof Cancellable) {
                    boolean prior = getCancelState(event);

                    listener.callEvent(event);

                    // See if this plugin cancelled the event
                    if (!prior && getCancelState(event)) {
                        invokeCancelled(getPlugin(), (TEvent) event);
                    }
                } else {
                    listener.callEvent(event);
                }
            }
        };
    }

    private void invokeCancelled(Plugin plugin, TEvent event) {
        for (CancelListener<TEvent> listener : listeners) {
            listener.onCancelled(plugin, event);
        }
    }

    private boolean getCancelState(Event event) {
        return ((Cancellable) event).isCancelled();
    }

    public void close() {
        if (backup != null) {
            try {
                HandlerList list = getHandlerList(eventClazz);
                getSlotsField(list).set(list, backup);

                Field handlers = list.getClass().getDeclaredField("handlers");
                handlers.setAccessible(true);
                handlers.set(list, null);

            } catch (Exception e) {
                throw new RuntimeException("Unable to clean up handler list.", e);
            }

            backup = null;
        }
    }

    /**
     * Retrieve the handler list associated with the given class.
     *
     * @param clazz  - given event class.
     * @return Associated handler list.
     */
    private static HandlerList getHandlerList(Class<? extends Event> clazz) {
        // Class must have Event as its superclass
        while (clazz.getSuperclass() != null && Event.class.isAssignableFrom(clazz.getSuperclass())) {
            try {
                Method method = clazz.getDeclaredMethod("getHandlerList");
                method.setAccessible(true);
                return (HandlerList) method.invoke(null);
            } catch (NoSuchMethodException e) {
                // Keep on searching
                clazz = clazz.getSuperclass().asSubclass(Event.class);
            } catch (Exception e) {
                throw new IllegalPluginAccessException(e.getMessage());
            }
        }
        throw new IllegalPluginAccessException("Unable to find handler list for event "
                + clazz.getName());
    }

    /**
     * Represents a registered listener that delegates to a given listener.
     * @author Kristian
     */
    private static class DelegatedRegisteredListener extends RegisteredListener {
        private final RegisteredListener delegate;

        public DelegatedRegisteredListener(RegisteredListener delegate) {
            // These values will be ignored however'
            super(delegate.getListener(), null, delegate.getPriority(), delegate.getPlugin(), false);
            this.delegate = delegate;
        }

        public void callEvent(Event event) throws EventException {
            delegate.callEvent(event);
        }

        public Listener getListener() {
            return delegate.getListener();
        }

        public Plugin getPlugin() {
            return delegate.getPlugin();
        }

        public EventPriority getPriority() {
            return delegate.getPriority();
        }

        public boolean isIgnoringCancelled() {
            return delegate.isIgnoringCancelled();
        }
    }
}