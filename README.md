# Tutorial Stream Kafka Topic ke Html dengan Server Sent Event (SSE) dengan Quarkus (Bahasa Indonesia)

Tutorial ini akan melakukan hands on mengenai cara stream Kafka ke HTML dengan menggunakan metode Server Send Event (SSE).
Kelebihan SSE dibandingkan dengan menggunakan Web Socket (ws://) adalah SSE menggunakan protokol http(s) dan satu arah, hanya dari server ke client saja.

[Quarkus.io](quarkus.io) adalah framework Kubernetes-Native Java yang dirancang khusus untuk Java Virtual Machine (JVM) seperti GraalVM dan HotSpot. Disponsori oleh Red Hat, Quarkus mengoptimalkan Java secara khusus untuk Kubernetes dengan mengurangi ukuran aplikasi Java, ukuran *image container*, dan jumlah memori yang diperlukan untuk menjalankan *image* tersebut.

Prerequsite tutorial ini adalah:
- Java 8 
- Maven **3.6.2 keatas** -> versi dibawah ini tidak didukung
- IDE kesayangan anda (RedHat CodeReady, Eclipse, VSCode, Netbeans, InteliJ, dll)
- Git client
- Dasar pemrograman Java

## Step 1: Install Apache Kafka dan buat topik baru
```bash
> wget https://www.apache.org/dyn/closer.cgi?path=/kafka/2.4.1/kafka_2.12-2.4.1.tgz
> tar -xzf kafka_2.12-2.4.1.tgz
> cd kafka_2.12-2.4.1
```

### Jalankan Kafka Server
```bash
> bin/zookeeper-server-start.sh config/zookeeper.properties
> bin/kafka-server-start.sh config/server.properties
```
Langkah diatas dilakukan untuk menjalankan server zookeeper di port **2181** dan server Kafka di port **9092**

### Buat Topic baru
```bash
> bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1 --topic mytopic
```
Langkah diatas dilakukan untuk membuat Topic Kafka baru dengan nama *mytopic* dengan jumlah partisi *1* dengan faktor replikasi sebanyak *1*

## Step 2: Clone code dari GitHub saya
Download source code dari repository [GitHub](https://github.com/erfinfeluzy/training-quarkus-sse-kafka.git) saya:
```bash
> git clone https://github.com/erfinfeluzy/training-quarkus-sse-kafka
> cd training-quarkus-sse-kafka
> mvn quarkus:dev
```
Struktur code sebagai berikut:\

![code structure](https://github.com/erfinfeluzy/training-quarkus-sse-kafka/blob/master/code-structure.png?raw=true)

Buka browser dengan url [http://localhost:8080](http://localhost:8080). akan terlihat halaman sbb:\

![code structure](https://github.com/erfinfeluzy/training-spring-sse-kafka/blob/master/result-on-browser.png?raw=true)

> Note: Untuk browser modern (eg: Chrome, Safari,etc) sudah mensupport untuk Server Sent Event (SSE). Hal ini mungkin tidak berjalan di IE :)

## Pembahasan Kode

### Library yang dibutuhkan
Tambahkan library kafka client pada file pom.xml
```xml
<dependency>
	<groupId>org.springframework.kafka</groupId>
	<artifactId>spring-kafka</artifactId>
</dependency>
```

### Konfigurasi Kafka Consumer
```java
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

	@Bean
	public ConsumerFactory<String, String> consumerFactory() {
		Map<String, Object> props = new HashMap<>();
		
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "group-id");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		
		return new DefaultKafkaConsumerFactory<>(props);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
		
		ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
		
		factory.setConsumerFactory(consumerFactory());
		
		return factory;
	}

}
```
### Konfigurasi Kafka Producer
```java
@Configuration
public class KafkaProducerConfig {

	@Bean
	public ProducerFactory<String, String> producerFactory() {
		Map<String, Object> configProps = new HashMap<>();
		configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		return new DefaultKafkaProducerFactory<>(configProps);
	}

	@Bean
	public KafkaTemplate<String, String> kafkaTemplate() {
		return new KafkaTemplate<>(producerFactory());
	}
}
```

### Publish Random message ke Topic Kafka setiap 5 detik

Snippet berikut pada file *KafkaTopicGenerator.java* untuk mempublish random data ke Topic dengan nama **mytopic**.
```java
@Async
@Scheduled(fixedRate = 5000)
public void doNotify() throws IOException {

	//randomly generate kafka message to topic:mytopic every 5 seconds
	kafkaTemplate.send("mytopic", "Data tanggal : " + new Date () + "; id : " + UUID.randomUUID() );
}
```

### Consume Topic Kafka dan teruskan ke Server Send Event emitter.
Snippet dibawah untuk subscribe ke topic **mytopic**.
```java
@KafkaListener(topics = "mytopic", groupId = "consumer-group-id-1")
public void listen(@Payload String message, @Header(KafkaHeaders.OFFSET) String offset) {

	//process incoming message from kafka
	doNotify("Kafka Offset=" + offset + "; message=" + message);	
}
```
Kemudian data dari topic di teruskan ke SSE emitter.
```java
private void doNotify(String message) {
	List<SseEmitter> deadEmitters = new ArrayList<>();
		
	emitters.forEach(emitter -> {
		try {
			//send message to frontend
			emitter
				.send(SseEmitter.event()
					.data(message));
				
		} catch (Exception e) {
			deadEmitters.add(emitter);
		}
	});
	
	emitters.removeAll(deadEmitters);
}
```
### Create Controller untuk melakukan stream SSE
Snippet berikut untuk melakukan stream data via http dengan menggunakan SSE pada endpoint **/stream**.
```java
@RestController
public class StreamController {
	
	@Autowired
	KafkaConsumer kafkaConsumer;

	@GetMapping("/stream")
	SseEmitter  stream() throws IOException {

		final SseEmitter emitter = new SseEmitter();
		kafkaConsumer.addEmitter(emitter);
		
		emitter.onCompletion(() -> kafkaConsumer.removeEmitter(emitter));
		emitter.onTimeout(() -> kafkaConsumer.removeEmitter(emitter));
		
		return emitter;

	}

}
```
Hasil dapat dicek dengan menggunakan perintah curl
```bash
> curl http://localhost:8080/stream
```
### Stream data ke HTML
Snippet javascript dibawah digunakan untuk menampilkan SSE event pada halaman html
```java
function initialize() {
	const eventSource = new EventSource('http://localhost:8080/stream');
	eventSource.onmessage = e => {
		const msg = e.data;
		document.getElementById("mycontent").innerHTML += "<br/>" + msg;
	};
	eventSource.onopen = e => console.log('open');
	eventSource.onerror = e => {
		if (e.readyState == EventSource.CLOSED) {
			console.log('close');
		}
		else {
			console.log(e);
		}
	};
	eventSource.addEventListener('second', function(e) {
		console.log('second', e.data);
	}, false);
}
window.onload = initialize;
```

## Voila! Kamu sudah berhasil
Kamu dapat memcoba dengan menggunakan browser pada url berikut [http://localhost:8080](http://localhost:8080).

## Bonus! Deploy aplikasi kamu ke Red Hat Openshift
Deploy aplikasi kamu dengan mudah menggunakan fitur Source to Image (s2i) pada OpenShift.
> Note: install CodeReady Container (crc) untuk lebih mudah.
```
> oc login
> oc new-project my-project
> oc new-app redhat-openjdk18-openshift~https://github.com/erfinfeluzy/training-spring-sse-kafka.git 
> oc expose svc/training-spring-sse-kafka
> oc get route
```

hasil dari perintah *oc get route* barupa url yang dapat dibuka di browser anda.
