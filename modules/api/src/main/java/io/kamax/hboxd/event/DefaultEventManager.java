/*
 * Hyperbox - Virtual Infrastructure Manager
 * Copyright (C) 2013 Maxime Dor
 * 
 * http://kamax.io/hbox/
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.hboxd.event;

import io.kamax.hbox.event._Event;
import io.kamax.hbox.exception.HyperboxException;
import io.kamax.hboxd.security.SecurityContext;
import io.kamax.tools.logging.Logger;
import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import net.engio.mbassy.bus.error.PublicationError;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class DefaultEventManager implements _EventManager, Runnable {

    private BlockingQueue<_Event> eventsQueue;
    private MBassador<_Event> eventBus;
    private boolean running = false;
    private Thread worker;

    @Override
    public void start() throws HyperboxException {

        Logger.debug("Event Manager Starting");
        eventBus = new MBassador<_Event>(BusConfiguration.Default());
        eventBus.addErrorHandler(new IPublicationErrorHandler() {

            @Override
            public void handleError(PublicationError error) {
                Logger.error("Failed to dispatch event " + error.getPublishedObject(), error.getCause());
            }

        });
        eventsQueue = new LinkedBlockingQueue<_Event>();
        worker = new Thread(this, "EvMgrWT");
        worker.setDaemon(true);
        SecurityContext.addAdminThread(worker);

        worker.start();
        Logger.verbose("Event Manager Started");
    }

    @Override
    public void stop() {

        Logger.debug("Event Manager Stopping");
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(1000);
            } catch (InterruptedException e) {
                Logger.exception(e);
            }
        }
        eventsQueue = null;
        Logger.verbose("Event Manager Stopped");
    }

    @Override
    public void register(Object o) {

        eventBus.subscribe(o);
        Logger.debug(o + " has registered for all events.");
    }

    @Override
    public void unregister(Object o) {

        eventBus.unsubscribe(o);
        Logger.debug(o + " has unregistered for all events.");
    }

    @Override
    public void post(_Event ev) {

        Logger.debug("Received Event ID [" + ev.getEventId() + "] fired @ " + ev.getTime());
        if ((eventsQueue != null) && !eventsQueue.offer(ev)) {
            Logger.error("Event queue is full (" + eventsQueue.size() + "), cannot add " + ev.getEventId());
        }
    }

    @Override
    public void run() {

        Logger.verbose("Event Manager Worker Started");
        running = true;
        while (running) {
            try {
                _Event event = eventsQueue.take();
                Logger.debug("Processing Event: " + event.toString());
                eventBus.publish(event);
            } catch (InterruptedException e) {
                Logger.debug("Got interupted, halting...");
                running = false;
            } catch (Throwable t) {
                Logger.error("Error when processing event: " + t.getMessage());
                Logger.exception(t);
            }
        }
        Logger.verbose("Event Manager Worker halted.");
    }

}
