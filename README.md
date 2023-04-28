## RocketMQ

1. tag特性：推荐使用不同的topic来实现此特性
2. rocketmq必填producerGroup的要求，默认填入`DEFAULT_PRODUCER_GROUP`
3. rocketmq消费者默认使用push模式（实际使用的也是pull，兼容了一些消费的负载压力），更为友好。
4. 默认consumer -> MessageModel消费模式是`CLUSTERING`
5. 注意：不同的topic请配置不同的group

## Kafka

1. kafka中消费者无BROADCASTING消费模式，内部使用`[consumerGroupName]_$random.uuid`来实现。配置`messageModel: BROADCASTING`

## 重点：

* 顺序消费：只能保证提交到MQ中落盘的顺序，并不能保证生产者生产的顺序一致。 比如：多个生产者生产消息，因为一些请求延迟，导致持久化的顺序落后。
  **_建议在业务上消息体中使用时间戳字段来进行区分_**
* 分布式MQ使用严格的全局顺序，需要将topic分区设置为1，而一个分区的消费者无论起多少个consumer实例只能有一个consumer。所以推荐使用局部有序。
* 局部有序：
    1. 生产者：设置发送消息时使用的分区字段`partitionKeyExpression: headers['xxx']`,在message header中必须包含此xxx字段值。mq会根据此字段值进行分片的路由。
    2. 消费者：配置`orderly: true`；rocketmq对应的消费的源码参见：`ConsumeMessageConcurrentlyService`,`ConsumeMessageOrderlyService`,
       因为rocketmq对拉取下来的数据可以选择并发消费（默认），或单线程for循环递归消费，所以会有此区分；
       而kafka只需要将数据根据分片路由规则，发送到固定分片即可，默认单线程处理（详见：`KafkaMessageListenerContainer # pollAndInvoke()`）不需要特殊处理。
       `kafkaListener$concurrency`
       作用是启动一个kafkaListener多少个消费者实例（默认是1）`ConcurrentKafkaListenerContainerFactory#setConcurrency`


* 消费offset：
    1. rocketmq默认CONSUME_FROM_LAST_OFFSET,kafka默认startOffset = earliest， 即一个新的consumer(consumerGroupId)
       在rocketmq中会从latest开始消费，而在kafka中会从earliest。 详见kafka源码：`KafkaMessageChannelBinder#createKafkaConsumerFactory`

* 如果使用TenantMessageData<T>进行发送，**只关心业务数据的情况下**(消息的header会进行丢弃)，请在消费端使用TenantMessageData<T>进行接受消费。 也可以使用Message<String>
  进行消费，但payload数据需要手动进行序列化,这种方式可以保存原始的message header。

* 使用了自定义配置的binding，代码会自动声明MessageChannel/SubscribableChannel和@StreamListener，不需要再使用原生方式声明。

* 关于Topic自动创建，指定分区个数：
    1. rocketmq 应用程序中自动创建topic时候，默认分区数为4，不可控制。
    2. kafka **_自动创建topic_**,_**自动增加分区数**_，参考[github-SpringCloudStreamKafka]
       `spring.cloud.stream.kafka.binder.minPartitionCount`
       注意：无topic的情况下，producer和consumer都会触发自动创建topic。但消费者和生产者创建的分区数计算方式是不同的
[![img_2.png](img_2.png)](README.md)
  * 开启binder的auto-create-topics & autoAddPartitions情况下，producer和consumer都会调用`createTopicAndPartitions`去自动调整分区数量，producer -> partitionCount,consumer -> instanceCount * concurrency。不会缩分区只会扩分区数
  * 建议由producer来控制分区数量，因为消费者可以启动多个实例，每个实例使用少量的线程分担消费。如：producer$partitionCount=20；消费者实例启动4个，consumer$concurrency=5

* concurrency在各MQ上实现的源码：
    1. KafkaMessageChannelBinder
       `int concurrency = usingPatterns ? extendedConsumerProperties.getConcurrency()
       : Math.min(extendedConsumerProperties.getConcurrency(), listenedPartitions.size());`
    2. RocketMQ
       `ConsumeMessageOrderlyService$consumeExecutor   
       ConsumeMessageConcurrentlyService$consumeExecutor,submitConsumeRequest(...) `

* 消费者中concurrency如果不配置，默认是单线程消费。

在spring生命周期过程中，源码的作用图。
![img_1.png](img_1.png)

* BindingBeansEnhanceRegistrar：

1. 扫描注册原生input和output定义
2. 根据自定义配置使用字节码工具生成自定义的input和output
3. 再扫描声明bindings的载体接口


* ConfigurationPropertiesBeanPostProcessor

1. 自定义配置加入到BindingServiceProperties配置实体中(BindingServiceProperties$binders,BindingServiceProperties$bindings)
2. 管理自定义配置中binder和bindings的关系，填充CustomizeScsPropertiesHolder

* StreamListenerAnnotationAutoGenerateBeanPostProcessor

1. 筛选自定义配置中input类型的bindings，注册streamListener信息和统一的消费回调模版方法doConsumer
2. _原生方式声明的inputBindings还是需要自己实现@StreamListener的消费方法_


* BinderFactoryListener： 在bindProducer/bindConsumer过程中，
    1. getBinder 【-> doGetBinder -> getBinderInstance(String configurationName),提供一个listener钩子，
       可以去修改binder中的成员变量（包括xxxProducerProperties,xxxConsumerProperties）】 ->
       doBindProducer【修改后的ProducerProperties和ConsumerProperties是实例化Binding的入参】

**注意：加强版本也支持原生SpringCloudStream的用法，可以共存，但不要混合使用（混合使用：配置用自定义，声明使用原生的注解 or 配置用原生，声明使用加强包提供的方式）。**


[SpringCloudStream-kafka]: https://cloud.spring.io/spring-cloud-stream-binder-kafka/spring-cloud-stream-binder-kafka.html#_apache_kafka_binder

[github-SpringCloudStreamKafka]: https://cloud.spring.io/spring-cloud-stream-binder-kafka/spring-cloud-stream-binder-kafka.html#_apache_kafka_binder