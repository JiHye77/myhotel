package hotelreservation;

import hotelreservation.config.kafka.KafkaProcessor;

import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired OrderRepository orderRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReserveAccepted_UpdateStatus(@Payload ReserveAccepted reserveAccepted){

        if(!reserveAccepted.validate()) return;

        System.out.println("\n\n##### listener UpdateStatus : " + reserveAccepted.toJson() + "\n\n");

        // Sample Logic //
        Optional<Order> orderOptional =  orderRepository.findById(reserveAccepted.getOrderId());
        Order order = orderOptional.get();
        order.setStatus(reserveAccepted.getStatus());

        orderRepository.save(order);
            
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReviewCreated_UpdateStatus(@Payload ReviewCreated reviewCreated){

        if(!reviewCreated.validate()) return;

        System.out.println("\n\n##### listener UpdateStatus : " + reviewCreated.toJson() + "\n\n");

        // Sample Logic //
        Optional<Order> orderOptional =  orderRepository.findById(reviewCreated.getOrderId());
        Order order = orderOptional.get();
        order.setStatus(reviewCreated.getStatus());

        orderRepository.save(order);
            
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
