package com.javieraviles.splitthemonolith.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class DomainEventPublisher {

	private final ApplicationEventPublisher publisher;

	public DomainEventPublisher(final ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public void publish(final Object event) {
		publisher.publishEvent(event);
	}
}
