package hotelreservation;

import hotelreservation.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class MypageViewHandler {


    @Autowired
    private MypageRepository mypageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrdered_then_CREATE_1 (@Payload Ordered ordered) {
        try {

            if (!ordered.validate()) return;

            // view 객체 생성
            Mypage mypage = new Mypage();
            // view 객체에 이벤트의 Value 를 set 함
            mypage.setOrderId(ordered.getId());
            mypage.setName(ordered.getName());
            mypage.setGuest(ordered.getGuest());
            mypage.setStatus(ordered.getStatus());
            // view 레파지 토리에 save
            mypageRepository.save(mypage);
        
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    // @StreamListener(KafkaProcessor.INPUT)
    // public void whenPaymentApproved_then_UPDATE_1(@Payload PaymentApproved paymentApproved) {
    //     try {
    //         if (!paymentApproved.validate()) return;
    //             // view 객체 조회
    //         List<Mypage> mypageList = mypageRepository.findByOrderId(paymentApproved.getOrderId());
    //         for(Mypage mypage : mypageList){
    //             // view 객체에 이벤트의 eventDirectValue 를 set 함
    //             mypage.setStatus(paymentApproved.getStatus());
    //             mypage.setPaymentId(paymentApproved.getId());
    //             // view 레파지 토리에 save
    //             mypageRepository.save(mypage);
    //         }
            
    //     }catch (Exception e){
    //         e.printStackTrace();
    //     }
    // }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenReserveAccepted_then_UPDATE_2(@Payload ReserveAccepted reserveAccepted) {
        try {
            if (!reserveAccepted.validate()) return;
                // view 객체 조회
            List<Mypage> mypageList = mypageRepository.findByOrderId(reserveAccepted.getOrderId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setStatus(reserveAccepted.getStatus());
                mypage.setReservationId(reserveAccepted.getId());
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenReviewCreated_then_UPDATE_3(@Payload ReviewCreated reviewCreated) {
        try {
            if (!reviewCreated.validate()) return;
                // view 객체 조회
            List<Mypage> mypageList = mypageRepository.findByOrderId(reviewCreated.getOrderId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setText(reviewCreated.getText());
                mypage.setStatus(reviewCreated.getStatus());
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}