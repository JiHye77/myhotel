
# [ 호텔 예약 ]

Final Project AWS 3차수 - 이지혜 

# Table of contents

- [호텔예약](#---)
  - [서비스 시나리오](#시나리오)
  - [분석/설계](#분석-설계)
  - [구현:](#구현)
    - [DDD 의 적용](#ddd-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [Correlation](#Corrlation)                         
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)       
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
    - [API Gateway](#API-게이트웨이-(gateway))                            
    - [SAGA-CQRS](#마이페이지)   
    -                      
  - [운영](#운영)
    - 컨테이너 이미지 생성 및 배포(#컨테이너-이미지-생성-및-배포) 
    - [동기식 호출 / Circuit Breaker](#동기식-호출--Circuit-Breaker) 
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포(Readiness Probe)](#무정지-배포(Readiness-Probe))
    - [Self Healing(Liveness Probe)](#Self-Healing(Liveness-Probe))
    - [ConfigMap / Persistence Volume](#Config-Map/Persistence-Volume) 


## 시나리오  

호텔 예약 시스템에서 요구하는 기능/비기능 요구사항은 다음과 같습니다. 
사용자가 호텔 예약 시 결제를 진행하면
호텔 관리자가 예약을 확정하는 시스템입니다.
사용자는 진행상황을 mypage에서 확인할 수 있습니다.


#### 기능적 요구사항

1. 고객이 원하는 일자를 선택하고 예약한다.
2. 고객이 결제를 진행한다.
3. 결제 후, 예약이 신청 되면 신청내역이 호텔에 전달된다. 
4. 호텔 관리자가 신청내역을 확인하여 예약을 확정한다.
5. 호텔 예약했던 고객은 Review를 작성할 수 있다.
6. 고객이 예약 진행상태 및 Review 정보를 원할 때마다 조회한다.


#### 비 기능적 요구사항

1. 트랜잭션
   - 결제가 되지 않으면 호텔 예약 신청이 처리되지 않아야 한다. `Sync 호출(req/rep)`  
   - 예약 내역이 없으면 review 작성할 수 없다. (order가 있어야 review 가능).  `Sync 호출(req/rep)`  

2. 장애격리
   - 호텔 관리자 기능이 수행 되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다. `Pub/Sub`  
   - 결제 시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도 한다. `(장애처리)`  

3. 성능
   - 고객이 예약 확인 상태를 마이페이지에서 확인할 수 있어야 한다. `CQRS`


# 분석 설계

## Event Storming

#### ver1 - 이벤트도출
 - MSAEZ 툴에서 이벤트스토밍 작업  
![image](https://user-images.githubusercontent.com/84304007/125014364-8b3b9f00-e0a8-11eb-956a-6a74cd37b0a5.png)  

#### ver2 - relation정의  
![image](https://user-images.githubusercontent.com/84304007/125014197-3861e780-e0a8-11eb-9f73-5b86a02edeb0.png)  

#### ver3 - attribute생성
![image](https://user-images.githubusercontent.com/84304007/124426010-5aa3ee80-dda4-11eb-9eaa-87b07bd30491.png)  

 - http://www.msaez.io/#/storming/ZI0N0eczMndHZbfdoWRoAVzzp4Q2/mine/fca5d4a029e24554953cdc46b01e5422  

### 기능 요구사항을 커버하는지 검증
1. 고객이 원하는 일자를 선택하고 예약한다.  -> O
2. 고객이 결제를 진행한다.  -> O
3. 결제 후, 예약이 신청 되면 신청내역이 호텔에 전달된다.   -> O
4. 호텔 관리자가 신청내역을 확인하여 예약을 확정한다.  -> O
5. 호텔 예약했던 고객은 Review를 작성할 수 있다.  -> O
6. 고객이 예약 진행상태 및 Review 정보를 원할 때마다 조회한다.  -> O


### 비기능 요구사항을 커버하는지 검증

1. 트랜잭션
   - 결제가 되지 않으면 검진예약 신청이 처리되지 않아야 한다. `Sync 호출` -> O  
   ==>  Request-Response 방식 처리
   
2. 장애격리
   - 호텔 관리자 기능이 수행 되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다. `Pub/Sub` -> O  
   ==>  Pub/Sub 방식으로 처리(Pub/Sub)
   - 결제 시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도 한다.  
     (장애처리)

3. 성능
   - 고객이 예약 확인 상태를 마이페이지에서 확인할 수 있어야 한다. `CQRS`


## 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현.
각 서비스 별로 포트넘버 부여 확인 ( 8081 ~ 8085 )

### 포트넘버 분리 (Gateway의 application.yml 파일)
![image](https://user-images.githubusercontent.com/84304007/124426249-c1c1a300-dda4-11eb-893d-34435b3079a1.png)

```
### 각 서비스를 수행

cd /home/project/myhotel/payment  
mvn spring-boot:run

cd /home/project/myhotel/reservation  
mvn spring-boot:run

cd /home/project/myhotel/order  
mvn spring-boot:run

cd /home/project/myhotel/review  
mvn spring-boot:run

cd /home/project/myhotel/customer  
mvn spring-boot:run

netstat -anlp | grep :808  
```

## DDD (Domain Driven Design) 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다  
- 업무 영역을 Biz. 중심으로 쪼개서 design함  
- (예시는 order 마이크로 서비스).
```
package hotelreservation;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Order_table")
public class Order {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String roomType;
    private Long cardNo;
    private Integer guest;
    private String name;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        hotelreservation.external.Payment payment = new hotelreservation.external.Payment();
        // mappings goes here
        payment.setOrderId(this.getId());
        payment.setCardNo(this.getCardNo());
        payment.setStatus("Paid");

        OrderApplication.applicationContext.getBean(hotelreservation.external.PaymentService.class)
            .pay(payment);

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }
    public Long getCardNo() {
        return cardNo;
    }

    public void setCardNo(Long cardNo) {
        this.cardNo = cardNo;
    }
    public Integer getGuest() {
        return guest;
    }

    public void setGuest(Integer guest) {
        this.guest = guest;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
}

```

- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 
데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package hotelreservation;

import java.util.Optional;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="orders", path="orders")
public interface OrderRepository extends PagingAndSortingRepository<Order, Long>{

    Optional<Order> findById(Long id);

}

```

- 적용 후 REST API 의 테스트
```
# 호텔 서비스의 주문처리
http localhost:8081/orders roomType=single guest=123 name=Jihye

# pay 서비스의 결제처리
http localhost:8083/payments orderId=1 cardNo=111

# reservation 서비스의 예약처리
http localhost:8082/reservations orderId=1 status="Reserved"

# review 서비스 등록 처리
http http://localhost:8085/reviews orderId=1 text="Excellent"

# 주문 상태 확인(mypage)

root@labs-412292045:/home/project/myhotel# http localhost:8084/mypages/1
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Thu, 08 Jul 2021 02:57:15 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "mypage": {
            "href": "http://localhost:8084/mypages/1"
        },
        "self": {
            "href": "http://localhost:8084/mypages/1"
        }
    },
    "guest": 123,
    "name": "Jihye",
    "orderId": 1,
    "reservationId": 1,
    "status": "Reviewed",
    "text": "Excellent"
}

```

## Correlation/CQRS 적용
PolicyHandler에서 처리 시 어떤 건에 대한 처리인지를 구별하기 위한 Correlation-key 구현을 
이벤트 클래스 안의 변수로 전달받아 서비스간 연관된 처리를 정확하게 구현했음.  

아래의 구현 예제를 보면  

고객이 호텔 예약(order)을 하면 동시에 결제(payment) 등의 서비스 상태가 변경이 되고,   
숙박 이후, 고객이 해당 order의 review를 생성하면 order의 상태도 적절하게 변경 된다.   
또한, 모든 정보는 mypage에서 조회 가능하다.  

1) 고객이 호텔 예약  
![image](https://user-images.githubusercontent.com/84304007/125005859-c386b180-e097-11eb-93af-e978a19976e0.png)  

2) 결제 상태 paid  
![image](https://user-images.githubusercontent.com/84304007/125005909-e1ecad00-e097-11eb-8fa8-479248030ad0.png)  

3) 예약 상태 변경  
![image](https://user-images.githubusercontent.com/84304007/125005965-fd57b800-e097-11eb-8305-f1bfa409c409.png)

4) mypage에 모두 저장됨  
![image](https://user-images.githubusercontent.com/84304007/125005999-0ea0c480-e098-11eb-8066-61a913fec373.png)

5) 고객이 Review 등록  
![image](https://user-images.githubusercontent.com/84304007/125006040-25dfb200-e098-11eb-8970-c07ab8ddba2a.png)

6) Review 등록 하면 order와 mypage의 review 값 모두 반영됨  
![image](https://user-images.githubusercontent.com/84304007/125006072-3d1e9f80-e098-11eb-869b-21ab1e31f291.png)  
![image](https://user-images.githubusercontent.com/84304007/125006081-414abd00-e098-11eb-926d-0d31cbafd841.png)



## 폴리글랏 퍼시스턴스
비지니스 로직은 내부에 순수한 형태로 구현
그 이외의 것을 어댑터 형식으로 설계 하여 해당 비지니스 로직이 어느 환경에서도 잘 도착하도록 설계

![image](https://user-images.githubusercontent.com/84304007/124892969-49154d80-e015-11eb-8a4e-b46f3e4a988b.png)

폴리그랏 퍼시스턴스 요건을 만족하기 위해 DB 변경 (기존 h2를 hsqldb로 변경): order서비스의 pom.xml에서 적용해 보았음.   

```
<!--		<dependency>-->
<!--			<groupId>com.h2database</groupId>-->
<!--			<artifactId>h2</artifactId>-->
<!--			<scope>runtime</scope>-->
<!--		</dependency>-->

		<dependency>
			<groupId>org.hsqldb</groupId>
			<artifactId>hsqldb</artifactId>
			<version>2.4.0</version>
			<scope>runtime</scope>
		</dependency>

# 변경/재기동 후 예약 주문
root@labs-412292045:/home/project/myhotel# http localhost:8081/orders roomType=double name=CJR
 
HTTP/1.1 201 
Content-Type: application/hal+json;charset=UTF-8
Date: Thu, 08 Jul 2021 02:59:25 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://localhost:8081/orders/2"
        },
        "self": {
            "href": "http://localhost:8081/orders/2"
        }
    },
    "cardNo": null,
    "guest": null,
    "name": "CJR",
    "roomType": "double",
    "status": null
}

# 저장이 잘 되었는지 조회

root@labs-412292045:/home/project/myhotel# http localhost:8084/mypages/2

HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Thu, 08 Jul 2021 02:59:58 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "mypage": {
            "href": "http://localhost:8084/mypages/2"
        },
        "self": {
            "href": "http://localhost:8084/mypages/2"
        }
    },
    "guest": null,
    "name": "CJR",
    "orderId": 2,
    "reservationId": 2,
    "status": "Reserved",
    "text": null
}
```


## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문(order)->결제(pay) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다.  
호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 
```
# (external) PaymentService.java

package hotelreservation.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

// @FeignClient(name="payment", url="http://payment:8080")
// @FeignClient(name="payment", url="http://localhost:8083")
@FeignClient(name="payment", url="${feign.client.url.paymentUrl}")
public interface PaymentService {

    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void pay(@RequestBody Payment payment);

}                      
```

- 주문을 받은 직후(@PostPersist) 결제를 요청하도록 처리
```
# Order.java (Entity)
    @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        hotelreservation.external.Payment payment = new hotelreservation.external.Payment();
        // mappings goes here
        payment.setOrderId(this.getId());
        payment.setCardNo(this.getCardNo());
        // payment.setStatus(this.getStatus());
        payment.setStatus("Paid");

        OrderApplication.applicationContext.getBean(hotelreservation.external.PaymentService.class)
            .pay(payment);
    }
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:     
```
# 결제 (payment) 서비스를 잠시 내려놓음 (ctrl+c) 
```

#주문처리 후, Fail error 확인
![image](https://user-images.githubusercontent.com/84304007/124857377-279d6d00-dfe7-11eb-9e8d-2c5641524413.png)


#결제서비스 재기동
cd /home/project/myhotel/payment  
mvn spring-boot:run  

#주문처리 후, 주문 성공(Success)
![image](https://user-images.githubusercontent.com/84304007/124857622-98448980-dfe7-11eb-8d9e-cc33ab5f1440.png)


## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 호텔 예약 시스템으로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 
예약 시스템의 처리를 위하여 결제주문이 블로킹 되지 않도록 처리
 
- 이를 위하여 결제가 이루어지고 나면, 결제 승인 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)  
 
```
#Payment.java

package hotelreservation;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Payment_table")
public class Payment {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private Long cardNo;
    private String status;

    @PostPersist
    public void onPostPersist(){
        PaymentApproved paymentApproved = new PaymentApproved();
        BeanUtils.copyProperties(this, paymentApproved);
        paymentApproved.publishAfterCommit();


    }
```

- 예약서비스에서는 결제승인 이벤트에 대해서 이를 수신하여 상태를 변경하고, 자신의 정책을 처리하도록 PolicyHandler 를 구현한다.

```
# (reservation) PolicyHandler.java

package hotelreservation;

import hotelreservation.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired ReservationRepository reservationRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_AcceptReserve(@Payload PaymentApproved paymentApproved){

        if(!paymentApproved.validate()) return;

        System.out.println("\n\n##### listener AcceptReserve : " + paymentApproved.toJson() + "\n\n");

        // Sample Logic //
        Reservation reservation = new Reservation();
        reservation.setOrderId(paymentApproved.getOrderId());
        reservation.setStatus("Reserved"); 
        reservationRepository.save(reservation);
            
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}

}

```

예약시스템은 주문/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 예약시스템이 유지보수로 인해 잠시 내려간 상태라도 예약 주문을 받는데 문제가 없어야 한다.

```
# (reservation)예약 서비스를 잠시 내려놓음 (ctrl+c)

# 주문처리
http localhost:8081/orders roomType=double name=LJH   #Success  

# 결제처리
http localhost:8083/payments orderId=5  #Success  

#주문 상태 확인  
http localhost:8081/orders/5
```

#주문상태는 안바뀌나, 주문은 가능함 확인 : 주문상태 "null"   
![image](https://user-images.githubusercontent.com/84304007/124858834-c1feb000-dfe9-11eb-90dd-26d5069cdc44.png)  

#reservation 서비스 기동  
```  
cd /home/project/myhotel/reservation  
mvn spring-boot:run
```  

#주문상태 확인 : 주문 상태가 "Reserved"으로 확인    

![image](https://user-images.githubusercontent.com/84304007/124858906-e35f9c00-dfe9-11eb-87b7-97313e8fe9a4.png)  


## API 게이트웨이(gateway)

API gateway 를 통해 MSA 진입점을 통일 시킨다.

```
#gateway 스프링부트 App을 추가 후 application.yaml내에 각 마이크로 서비스의 routes 를 추가하고 gateway 서버의 포트를 8080으로 정의

---
spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://order:8080
          predicates:
            - Path=/orders/** 
        - id: reservation
          uri: http://reservation:8080
          predicates:
            - Path=/reservations/** 
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/payments/** 
        - id: customer
          uri: http://customer:8080
          predicates:
            - Path= /mypages/**
        - id: review
          uri: http://review:8080
          predicates:
            - Path=/reviews/** 
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080

```
#Gateway Deploy 생성 및 Kubernetes에 생성된 Deploy. 확인  
![image](https://user-images.githubusercontent.com/84304007/124870391-9e456500-dffd-11eb-8f78-f072bc2f6ab9.png)  

#API Gateay 엔드포인트 확인  
![image](https://user-images.githubusercontent.com/84304007/124870461-bae19d00-dffd-11eb-8cfa-a5a27312d037.png)  

#order 주문 서비스 호출 시 성공 (8080포트)
![image](https://user-images.githubusercontent.com/84304007/124870526-d51b7b00-dffd-11eb-951c-644728a33f9a.png)


## 마이페이지
# CQRS
- 고객이 예약건에 대한 Status를 조회할 수 있도록 CQRS로 구현하였음.
-  mypage 조회를 통해 모든 예약건에 대한 상태정보를 확인할 수 있음.

고객의 예약정보를 한 눈에 볼 수 있게 mypage를 구현 한다.(CQRS)

```
# mypage 호출 
http GET http://localhost:8084/mypages/1

HTTP/1.1 200
Content-Type: application/hal+json;charset=UTF-8
Date: Tue, 06 Jul 2021 06:05:23 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "mypage": {
            "href": "http://localhost:8084/mypages/1"
        },
        "self": {
            "href": "http://localhost:8084/mypages/1"
        }
    },
    "guest": 123,
    "name": "Jihye",
    "orderId": 1,
    "reservationId": 2,
    "status": "Reserved",
    "text": "Excellent"
}
```
- 여러개의 리스트 
```
{
    "_embedded": {
        "mypages": [
            {
                "_links": {
                    "mypage": {
                        "href": "http://localhost:8084/mypages/1"
                    },
                    "self": {
                        "href": "http://localhost:8084/mypages/1"
                    }
                },
                "guest": 123,
                "name": "Jihye",
                "orderId": 1,
                "reservationId": 2,
                "status": "Reserved",
                "text": "Excellent"
            },
            {
                "_links": {
                    "mypage": {
                        "href": "http://localhost:8084/mypages/2"
                    },
                    "self": {
                        "href": "http://localhost:8084/mypages/2"
                    }
                },
                "guest": null,
                "name": "JRJR",
                "orderId": 1,
                "reservationId": null,
                "status": null,
                "text": null
            },
            {
                "_links": {
                    "mypage": {
                        "href": "http://localhost:8084/mypages/3"
                    },
                    "self": {
                        "href": "http://localhost:8084/mypages/3"
                    }
                },
                "guest": null,
                "name": "CJR",
                "orderId": 2,
                "reservationId": 3,
                "status": "Reserved",
                "text": null
            }
        ]
    },
    "_links": {
        "profile": {
            "href": "http://localhost:8084/profile/mypages"
        },
        "search": {
            "href": "http://localhost:8084/mypages/search"
        },
        "self": {
            "href": "http://localhost:8084/mypages"
        }
    }
}
```

# 운영

## 컨테이너 이미지 생성 및 배포

###### ECR 접속 비밀번호 생성
```sh
aws --region "ca-central-1" ecr get-login-password
```
###### ECR 로그인
```sh
docker login --username AWS -p {ECR 접속 비밀번호} 879772956301.dkr.ecr.ca-central-1.amazonaws.com  
Login Succeeded
```
###### 마이크로서비스 빌드, order/payment/reservation/customer/review 각각 실행
```sh
mvn clean package -B
```
###### 컨테이너 이미지 생성
- docker build -t 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-order:v1 .  
- docker build -t 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-payment:v1 .
- docker build -t 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-reservation:v1 .
- docker build -t 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-customer:v1 .
- docker build -t 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-review:v1 .  
- docker build -t 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-gateway:v1 .  
![image](https://user-images.githubusercontent.com/84304007/124864403-cc25ac00-dff3-11eb-9998-4b7a3a732001.png)  
![image](https://user-images.githubusercontent.com/84304007/124864437-da73c800-dff3-11eb-889f-f64dde97490c.png)


###### ECR에 컨테이너 이미지 배포
- docker push 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-order:v1  
- docker push 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-payment:v1
- docker push 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-reservation:v1
- docker push 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-customer:v1
- docker push 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-review:v1  
- docker push 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-gateway:v1 
![image](https://user-images.githubusercontent.com/84304007/124864340-b57f5500-dff3-11eb-8d29-f63d15abcf27.png)  

###### 네임스페이스 hotelreservation 생성 및 이동
```sh
kubectl create namespace hotelreservation
kubectl config set-context --current --namespace=hotelreservation
```
###### EKS에 마이크로서비스 배포, order/payment/reservation/customer/review 각각 실행
```sh
kubectl create deploy order --image=879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-order:v1 -n hotelreservation  
kubectl expose deploy order --type="ClusterIP" --port=8080 --namespace=hotelreservation  

#Gateway는 LoadBalancer type으로 실행  

kubectl create deploy gateway --image=879772956301.dkr.ecr.ca-central-1.amazonaws.com/user17-gateway:v1 -n hotelreservation  
kubectl expose deploy gateway --type="LoadBalancer" --port=8080 --namespace=hotelreservation   
```

###### 마이크로서비스 배포 상태 확인
```sh
kubectl get pods  
```
![image](https://user-images.githubusercontent.com/84304007/124867605-5e7c7e80-dff9-11eb-8274-444a7e91d8d7.png)


```sh
kubectl get deployment  
```
![image](https://user-images.githubusercontent.com/84304007/124867641-70f6b800-dff9-11eb-8af5-029fd44b2147.png)


## 동기식 호출 / Circuit Breaker

* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함

시나리오는 주문(order) -->결제(pay) 시의 연결을 RESTful Request/Response 로 연동하여 구현이 되어있고, 결제 요청이 과도할 경우 CB 를 통하여 장애격리.

- Hystrix 를 설정:  요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정
- 임의의 부하처리를 위해 결제서비스내 sleep을 random하게 적용하였다.
```
# Order 서비스, application.yml에 추가  
```  
![image](https://user-images.githubusercontent.com/84304007/125008658-ecaa4080-e09d-11eb-9cd8-151afa003c06.png)  
![image](https://user-images.githubusercontent.com/84304007/125008728-0c416900-e09e-11eb-8aea-6a01093b3ab9.png)


* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:  
- 동시사용자 100명으로 부하 생성시 서킷 브레이커 동작 확인  
```  
siege -c100 -t60S -v --content-type "application/json" 'http://a6fb12afceb3241e5b3cee8a2f04e18c-312668797.ca-central-1.elb.amazonaws.com:8080/orders POST {"roomType": "double", "guest": "111"}'    

```
![image](https://user-images.githubusercontent.com/84304007/125009210-05ffbc80-e09f-11eb-803c-93477cb711be.png)  
 
 성공 251, 실패 317건 발생  



## 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 


- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다:
```
kubectl autoscale deploy payment --min=1 --max=10 --cpu-percent=15
```
- CB 에서 했던 방식대로 워크로드를 2분 동안 걸어준다.
```
siege -c100 -t120S -r10 --content-type "application/json" 'http://a6fb12afceb3241e5b3cee8a2f04e18c-312668797.ca-central-1.elb.amazonaws.com:8080/orders POST {"roomType": "double", "guest": "111"}'
```
- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
```
kubectl get deploy payment -w
```
- 어느정도 시간이 흐른 후 스케일 아웃이 벌어지는 것을 확인할 수 있다: --> 확인 못함

```
root@labs-412292045:/home/project# kubectl get deploy payment -w -n hotelreservation
NAME      READY   UP-TO-DATE   AVAILABLE   AGE
payment   1/1     1            1           139m
```


## 무정지 배포(Readiness Probe)

- 무정지 배포전 payment서비스의 replic를 3개로 확장.  
```
root@labs-412292045:/home/project# kubectl autoscale deploy payment --min=1 --max=3 --cpu-percent=15
```
#### Readiness 설정 
![2](https://github.com/mulcung03/AWS3_healthcenter/blob/main/refer/2.PNG)


#### 부하테스트 siege pod 설치 및 실행

충분한 시간만큼 부하를 주고,
그 사이 새로운 image 를 반영후 deployment.yml을 배포  
Siege 로그를 보면서 배포 시 무정지로 배포되는 것을 확인.  
```
root@labs-412292045:/home/project# siege -c100 -t60S -r10 -v --content-type "application/json" 'http://a6fb12afceb3241e5b3cee8a2f04e18c-312668797.ca-central-1.elb.amazonaws.com:8080/payments POST {"cardNo": "123"}'  --> 

```
![image](https://user-images.githubusercontent.com/84304007/125018487-2b48f680-e0b0-11eb-913d-e651f3162441.png)  



## Self Healing(Liveness Probe)
- deployment.yml 을 /tmp/healthy 파일을 만들고 90초 후 삭제 후 
livenessProbe에 /tmp/healthy 파일이 존재하는지 재확인하는 설정값을 추가
- periodSeconds 값으로 5초마다/tmp/healthy 파일의 존재 여부를 조회
- 파일이 존재하지 않을 경우, 정상 작동에 문제가 있다고 판단되어 kubelet에 의해 자동으로 컨테이너가 재시작

#### review deployment.yml 파일 수정
![image](https://user-images.githubusercontent.com/84304007/124918009-7a9b1280-e02f-11eb-9ede-27b3a6feb44c.png)  


#### 설정 수정된 상태 확인
```
# kubectl describe pod review -n hotelreservation
```
![image](https://user-images.githubusercontent.com/84304007/124918927-905d0780-e030-11eb-900a-9e5b5e49acc1.png)  

- 컨테이너 실행 후 90초 동인은 정상이나 이후 /tmp/healthy 파일이 삭제되어 livenessProbe에서 실패를 리턴하게 되고, pod 정상 상태 일 때 pod 진입하여 /tmp/healthy 파일 생성해주면 정상 상태 유지 확인 --> 확인 못함

```
# kubectl get po –n healthcenter –w
```

```
root@labs-412292045:/home/project/myhotel/review/kubernetes# kubectl get po -n hotelreservation -w
NAME                          READY   STATUS    RESTARTS   AGE
customer-7c5d45b9bb-mdfls     1/1     Running   0          6h30m
gateway-6458c69958-czpws      1/1     Running   0          6h29m
order-58fb8b46bd-ps9lf        1/1     Running   0          4h16m
payment-6b957d89f8-m4r6c      1/1     Running   0          3h49m
reservation-8588b9cc4-sxcll   1/1     Running   0          6h31m
review-986c9766-wn759         1/1     Running   0          15m
siege                         1/1     Running   0          79m
siege-5c7c46b788-9w5l5        1/1     Running   0          4h21m


* 아래와 같은 형태의 결과가 나와야 하나 확인 못함..

NAME                              READY   STATUS              RESTARTS   AGE
efs-provisioner-f4f7b5d64-zfkpg   0/1     ContainerCreating   0          39m
notification-57cb4df96b-2h4w9     1/1     Running             111        9h
order-647ccdbcd5-z5txt            1/1     Running             0          83s
payment-d48bfc5f9-mmn2m           1/1     Running             3          53m
reservation-857df7bfd8-wvb4c      1/1     Running             5          8m19s
siege                             1/1     Running             0          10h
reservation-857df7bfd8-wvb4c      0/1     Running             6          8m27s
reservation-857df7bfd8-wvb4c      1/1     Running             6          8m52s
reservation-857df7bfd8-wvb4c      0/1     OOMKilled            6          9m52s
reservation-857df7bfd8-wvb4c      0/1     Running             7          12m
reservation-857df7bfd8

```


## Config Map/Persistence Volume
- Persistence Volume

1: EFS 생성
```
EFS 생성 시 클러스터의 VPC를 선택해야함
```
![image](https://user-images.githubusercontent.com/84304007/124917885-53444580-e02f-11eb-91d4-458945d170c7.png)  
![image](https://user-images.githubusercontent.com/84304007/124917729-209a4d00-e02f-11eb-8956-40f90790ff11.png)  

2. EFS 계정 생성 및 ROLE 바인딩

3. EFS Provisioner 배포


4. 설치한 Provisioner를 storageclass에 등록


5. PVC(PersistentVolumeClaim) 생성


6. order pod 적용


7. A pod에서 마운트된 경로에 파일을 생성하고 B pod에서 파일을 확인함


#### Config Map

1: configmap.yml 파일 생성


2. deployment.yml에 적용하기
