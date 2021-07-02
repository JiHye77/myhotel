package hotelreservation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

 @RestController
 public class OrderController {

        @Autowired 
        OrderRepository orderRepository;

@RequestMapping(value = "/orders/checkOrder",
        method = RequestMethod.GET,
        produces = "application/json;charset=UTF-8")



public boolean checkOrder(@RequestParam("orderId") Long orderId)
        throws Exception {

                boolean status = false;

                Optional<Order> orderOptional = orderRepository.findById(orderId);
                Order order = orderOptional.get();

                if (order.getStatus()!=null) {
                        status = true;
                } 
                return status;

        }
 }
