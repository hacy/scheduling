/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.resourcemanager.common.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;
import org.objectweb.proactive.annotation.PublicAPI;
import org.ow2.proactive.resourcemanager.core.properties.PAResourceManagerProperties;
import org.ow2.proactive.resourcemanager.frontend.RMEventListener;
import org.ow2.proactive.resourcemanager.frontend.RMMonitoring;


/**
 * Defines a state of the Resource Manager for a Monitor.
 * In order to receive Resource Manager events,
 * a monitor register itself to {@link RMMonitoring} by
 * the method {@link RMMonitoring#addRMEventListener(RMEventListener listener, RMEventType... events)},
 * and get an initial state which is the snapshot of Resource Manager state, with its
 * nodes and NodeSources.
 *
 * @see RMNodeEvent
 * @see RMNodeSourceEvent
 * @see RMMonitoring
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 */
@PublicAPI
@XmlRootElement
public class RMInitialState implements Serializable {

    public static final Long EMPTY_STATE = -1l;

    private static final Logger LOGGER = Logger.getLogger(RMInitialState.class);

    /**
     * Nodes events
     */
    private Map<String, RMNodeEvent> nodeEvents = new ConcurrentHashMap<>();

    /**
     * Nodes sources AO living in RM
     */
    private Map<String, RMNodeSourceEvent> nodeSourceEvents = new ConcurrentHashMap<>();

    /**
     * keeps track of the latest (biggest) counter among the 'nodeEvents' and 'nodeSourceEvents'
     */
    private AtomicLong latestCounter = new AtomicLong(0);

    /**
     * ProActive empty constructor
     */
    public RMInitialState() {

    }

    /**
     * Creates an InitialState object.
     *
     * @param nodesEventList  RM's node events.
     * @param nodeSourcesList RM's node sources list.
     */
    public RMInitialState(Map<String, RMNodeEvent> nodesEventList, Map<String, RMNodeSourceEvent> nodeSourcesList) {
        this.nodeEvents = nodesEventList;
        this.nodeSourceEvents = nodeSourcesList;
    }

    /**
     * Current version of RM portal and maybe other clients expects "nodesEvents" inside JSON
     *
     * @return
     */
    public List<RMNodeEvent> getNodesEvents() {
        return Collections.unmodifiableList(new ArrayList<>(this.nodeEvents.values()));
    }

    /**
     * Current version of RM portal and maybe other clients expects "nodeSource" inside JSON
     *
     * @return
     */
    public List<RMNodeSourceEvent> getNodeSource() {
        return Collections.unmodifiableList(new ArrayList<>(this.nodeSourceEvents.values()));
    }

    public long getLatestCounter() {
        return latestCounter.get();
    }

    public void nodeAdded(RMNodeEvent event) {
        updateCounter(event);
        nodeEvents.put(event.getNodeUrl(), event);

    }

    public void nodeStateChanged(RMNodeEvent event) {
        updateCounter(event);
        nodeEvents.put(event.getNodeUrl(), event);
    }

    public void nodeRemoved(RMNodeEvent event) {
        updateCounter(event);
        nodeEvents.put(event.getNodeUrl(), event);
    }

    public void nodeSourceAdded(RMNodeSourceEvent event) {
        updateCounter(event);
        nodeSourceEvents.put(event.getSourceName(), event);
    }

    public void nodeSourceRemoved(RMNodeSourceEvent event) {
        updateCounter(event);
        nodeSourceEvents.put(event.getSourceName(), event);
    }

    public void nodeSourceStateChanged(RMNodeSourceEvent event) {
        updateCounter(event);
        nodeSourceEvents.put(event.getSourceName(), event);
    }

    private void updateCounter(RMEvent event) {
        latestCounter.set(Math.max(latestCounter.get(), event.getCounter()));
    }

    /**
     * Clones current state events, but keep only those events which has counter bigger than provided 'filter'
     * Event counter can take values [0, +).
     * So if filter is '-1' then all events will returned.
     * @param filter
     * @return rmInitialState where all the events bigger than 'filter'
     */
    public RMInitialState cloneAndFilter(long filter) {
        long actualFilter;
        if (filter <= latestCounter.get()) {
            actualFilter = filter;
        } else {
            LOGGER.info(String.format("Client is aware of %d but server knows only about %d counter. " +
                                      "Probably because there was network server restart.",
                                      filter,
                                      latestCounter.get()));
            actualFilter = EMPTY_STATE; // reset filter to default  value
        }
        RMInitialState clone = new RMInitialState();

        clone.nodeEvents = newFilteredEvents(this.nodeEvents,
                                             actualFilter,
                                             actualFilter + PAResourceManagerProperties.RM_REST_MONITORING_MAXIMUM_CHUNK_SIZE.getValueAsInt());
        clone.nodeSourceEvents = newFilteredEvents(this.nodeSourceEvents,
                                                   actualFilter,
                                                   actualFilter + PAResourceManagerProperties.RM_REST_MONITORING_MAXIMUM_CHUNK_SIZE.getValueAsInt());

        clone.latestCounter.set(Math.max(actualFilter,
                                         Math.max(findLargestCounter(clone.nodeEvents.values()),
                                                  findLargestCounter(clone.nodeSourceEvents.values()))));
        return clone;
    }

    private <T extends RMEvent> Map<String, T> newFilteredEvents(Map<String, T> events, long from, long to) {
        return events.entrySet()
                     .stream()
                     .filter(entry -> from < entry.getValue().getCounter() && entry.getValue().getCounter() <= to)
                     .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private <T extends RMEvent> long findLargestCounter(Collection<T> events) {
        final Optional<T> max = events.stream().max(Comparator.comparing(RMEvent::getCounter));
        return max.map(RMEvent::getCounter).orElse(0l);
    }
}
