package com.erfinfeluzy.demo;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecord;

@ApplicationScoped
public class KafkaTopicGenerator {

	@Inject
	@Channel("mytopic-publisher")
	private Emitter<String> emitter;

	@Scheduled(every = "5s")
	public void scheduler() {
		
		//randomly generate kafka message to topic:mytopic every 5 seconds	
		emitter.send( "Data tanggal : " + new Date () + "; id : " + UUID.randomUUID() );
	}

}
