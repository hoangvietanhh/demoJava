package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository.UserRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Date;
import java.util.Optional;

import static com.example.demo.config.RabbitMQConfig.QUEUE;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Autowired
    JobBuilderFactory jobBuilderFactory;

    @Autowired
    StepBuilderFactory stepBuilderFactory;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private String dateData = "";

    @Bean
    public FlatFileItemReader<User> itemReader() {
        FlatFileItemReader<User> flatFileItemReader = new FlatFileItemReader<>();
        flatFileItemReader.setResource(new FileSystemResource("src/main/resources/users.csv"));
        flatFileItemReader.setName("CSV-Reader");
        flatFileItemReader.setLinesToSkip(1);
        flatFileItemReader.setLineMapper(lineMapper());
        return flatFileItemReader;
    }

    @Bean
    public LineMapper<User> lineMapper() {
        DefaultLineMapper<User> defaultLineMapper = new DefaultLineMapper<>();
        DelimitedLineTokenizer lineTokenizer = new DelimitedLineTokenizer();

        lineTokenizer.setDelimiter(",");
        lineTokenizer.setStrict(false);
        lineTokenizer.setNames("id", "name", "salary");

        BeanWrapperFieldSetMapper<User> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(User.class);

        defaultLineMapper.setLineTokenizer(lineTokenizer);
        defaultLineMapper.setFieldSetMapper(fieldSetMapper);
        return defaultLineMapper;
    }


    @Bean
    public ItemProcessor<User, User> itemProcessor() {
        return new ItemProcessor<User, User>() {

            @Override
            public User process(final User user) throws Exception {
                user.setCreatedDate(new Date());
                return user;
            }
        };
    }

    @Bean
    public ItemWriter<User> itemWriter() {
        RepositoryItemWriter<User> writer = new RepositoryItemWriter<>();
        writer.setRepository(userRepository);
        writer.setMethodName("save");
        return writer;
    }

    @Bean
    public Job importUserJob() {
        return jobBuilderFactory.get("importUserJob")
                .incrementer(new RunIdIncrementer())
                .start(step())
                .next(step2())
                .next(step3())
                .build();
    }

    @Bean
    public Step step() {
        return stepBuilderFactory.get("step1")
                .<User, User>chunk(20)
                .reader(itemReader())
                .processor(itemProcessor())
                .writer(itemWriter())
                .build();
    }

    @Bean
    public Step step2() {
        return stepBuilderFactory.get("step2")
                .tasklet(tasklet())
                .build();
    }

    @Bean
    public Tasklet tasklet() {
        return (contribution, chunkContext) -> {
            Optional<User> user = userRepository.findById(1L);
            User user1 = user.get();
//            redisTemplate.opsForValue().set("user",  user1.getCreatedDate().toString());

            String date = user1.getCreatedDate().toString();
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, date);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step step3() {
        return stepBuilderFactory.get("step3")
                .tasklet(tasklet1())
                .build();
    }

    @RabbitListener(queues = QUEUE)
    private void consumerRabbit(String date) {
        dateData = date;
    }

    private Tasklet tasklet1() {
        return (contribution, chunkContext) -> {
            String dateRedis = redisTemplate.opsForValue().get("user").toString();
            if (dateData.equals(dateRedis))
                System.out.println("updated");
            else
                redisTemplate.opsForValue().set("user", dateData);
                System.out.println("updating data...");
            return RepeatStatus.FINISHED;
        };
    }
}
