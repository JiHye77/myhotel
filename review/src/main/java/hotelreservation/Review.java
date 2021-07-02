package hotelreservation;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Review_table")
public class Review {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private String text;
    private String status;

    @PostPersist
    public void onPostPersist(){


        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        hotelreservation.external.Order order = new hotelreservation.external.Order();
        // mappings goes here
        Boolean orderYN = ReviewApplication.applicationContext.getBean(hotelreservation.external.OrderService.class)
            .checkOrder(this.getOrderId());

            if (orderYN){
                ReviewCreated reviewCreated = new ReviewCreated();
                this.setStatus("Reviewed");
                BeanUtils.copyProperties(this, reviewCreated);
                // reviewCreated.setStatus("Reviewed");
                reviewCreated.publishAfterCommit();
            }

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




}
