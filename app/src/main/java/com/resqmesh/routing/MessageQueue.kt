package com.resqmesh.routing

import com.resqmesh.model.MessagePacket
import com.resqmesh.model.MessageType
import java.util.PriorityQueue

/**
 * Thread-safe Priority Queue for ResQMesh packets.
 *
 * Implements strict SOS-First Scheduling:
 * - SOS packets are always placed at the front of the queue and forwarded before all other traffic.
 * - Non-emergency packets are ordered by their priority and creation timestamp (FIFO within same priority).
 * - Under high buffer pressure, low-priority non-emergency packets are dropped first to protect SOS alerts.
 *
 * @param maxCapacity Maximum number of packets held in RAM before dropping lowest-priority items.
 */
class MessageQueue(private val maxCapacity: Int = 500) {

    private val lock = Any()
    private val queue = PriorityQueue<MessagePacket>()

    /**
     * Enqueues a packet for transmission or relaying.
     *
     * @param packet The [MessagePacket] to enqueue.
     * @return True if successfully added, false if dropped due to buffer constraints.
     */
    fun enqueue(packet: MessagePacket): Boolean {
        synchronized(lock) {
            if (packet.isExpired()) {
                return false
            }

            // If at capacity, evict the lowest-priority non-SOS item
            if (queue.size >= maxCapacity) {
                val evicted = evictLowestPriorityNonSos()
                if (!evicted && packet.messageType != MessageType.SOS) {
                    // Buffer is completely full of SOS alerts; cannot accept lower priority
                    return false
                }
            }

            queue.add(packet)
            return true
        }
    }

    /**
     * Enqueues multiple packets, preserving SOS prioritization.
     */
    fun enqueueAll(packets: Collection<MessagePacket>) {
        synchronized(lock) {
            packets.forEach { enqueue(it) }
        }
    }

    /**
     * Dequeues the highest priority packet (SOS alerts first).
     *
     * @return The next [MessagePacket], or null if the queue is empty.
     */
    fun dequeue(): MessagePacket? {
        synchronized(lock) {
            while (queue.isNotEmpty()) {
                val candidate = queue.poll()
                if (candidate != null && !candidate.isExpired()) {
                    return candidate
                }
                // If expired, loop continues to next candidate
            }
            return null
        }
    }

    /**
     * Peeks at the highest priority packet without removing it.
     */
    fun peek(): MessagePacket? {
        synchronized(lock) {
            return queue.peek()
        }
    }

    /**
     * Removes a specific packet from the queue (e.g. when cancelled or ACK received).
     */
    fun remove(packetId: String): Boolean {
        synchronized(lock) {
            val target = queue.firstOrNull { it.id == packetId }
            return if (target != null) {
                queue.remove(target)
            } else {
                false
            }
        }
    }

    /**
     * Returns the current number of valid packets in the queue.
     */
    fun size(): Int {
        synchronized(lock) {
            return queue.size
        }
    }

    /**
     * Checks if the queue contains no items.
     */
    fun isEmpty(): Boolean {
        synchronized(lock) {
            return queue.isEmpty()
        }
    }

    /**
     * Returns an immutable snapshot list of currently queued packets in priority order.
     */
    fun toList(): List<MessagePacket> {
        synchronized(lock) {
            return queue.sorted()
        }
    }

    /**
     * Clears all packets from the queue.
     */
    fun clear() {
        synchronized(lock) {
            queue.clear()
        }
    }

    /**
     * Finds and evicts the packet with the lowest priority that is not an SOS alert.
     */
    private fun evictLowestPriorityNonSos(): Boolean {
        // PriorityQueue orders highest priority first; find the lowest priority element from the back
        val nonSosItems = queue.filter { it.messageType != MessageType.SOS }
        if (nonSosItems.isEmpty()) return false

        val lowest = nonSosItems.minByOrNull { it.priority } ?: return false
        queue.remove(lowest)
        return true
    }
}
