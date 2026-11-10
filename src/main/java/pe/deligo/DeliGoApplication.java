package pe.deligo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeliGoApplication {
    public static void main(String[] args) { SpringApplication.run(DeliGoApplication.class, args); }
}
