package hotelreservation;

public class ReviewCreated extends AbstractEvent {

    private Long reviewId;
    private Long orderId;
    private String text;
    private String status;

    public Long getId() {
        return reviewId;
    }

    public void setId(Long reviewId) {
        this.reviewId = reviewId;
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