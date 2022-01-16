package com.allan.shoppingMall.domains.order.service;

import com.allan.shoppingMall.common.exception.ErrorCode;
import com.allan.shoppingMall.common.exception.ItemNotFoundException;
import com.allan.shoppingMall.common.exception.order.OrderNotFoundException;
import com.allan.shoppingMall.common.exception.order.payment.PaymentFailByValidatedAmountException;
import com.allan.shoppingMall.common.exception.order.payment.PaymentFailException;
import com.allan.shoppingMall.common.value.Address;
import com.allan.shoppingMall.domains.delivery.domain.Delivery;
import com.allan.shoppingMall.domains.delivery.domain.DeliveryStatus;
import com.allan.shoppingMall.domains.item.domain.clothes.ClothesRepository;
import com.allan.shoppingMall.domains.item.domain.clothes.Clothes;
import com.allan.shoppingMall.domains.item.domain.clothes.ClothesSize;
import com.allan.shoppingMall.domains.item.domain.clothes.ClothesSizeRepository;
import com.allan.shoppingMall.domains.member.domain.Member;
import com.allan.shoppingMall.domains.mileage.domain.model.MileageContent;
import com.allan.shoppingMall.domains.mileage.domain.model.MileageDTO;
import com.allan.shoppingMall.domains.mileage.service.MileageService;
import com.allan.shoppingMall.domains.order.domain.*;
import com.allan.shoppingMall.domains.order.domain.model.OrderDetailDTO;
import com.allan.shoppingMall.domains.order.domain.model.OrderItemDTO;
import com.allan.shoppingMall.domains.order.domain.model.OrderRequest;
import com.allan.shoppingMall.domains.order.domain.model.OrderSummaryDTO;
import com.allan.shoppingMall.domains.payment.domain.Payment;
import com.allan.shoppingMall.domains.payment.domain.PaymentRepository;
import com.allan.shoppingMall.domains.payment.domain.model.PaymentDTO;
import com.allan.shoppingMall.domains.payment.domain.model.iamport.PaymentIamportDTO;
import com.allan.shoppingMall.domains.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ClothesRepository clothesRepository;
    private final ClothesSizeRepository clothesSizeRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final MileageService mileageService;

    /**
     * 상품 상세페이지를 통해서 바로 주문하는 경우 사용하는 주문 메소드.
     * (주문 리스트에 상품들은 모두 동일한 종류의 상품들이다. 예) 의상 페이지에서 주문시, 주문 리스트 상품은 모두 의상 상품들.)
     * @param request 주문에 필요한 클라이언트단에서 전달하는 정보 오브젝트.
     * @param member 회원 도메인.
     * @return
     */
    @Transactional
    public String order(OrderRequest request, Member member){
        // 카테고리에 따른 분기 처리가 필요. 카테고리 추가시 코드 수정이 필요하다.

        Order order = Order.builder()
                .orderer(member)
                .delivery(Delivery.builder()
                        .address(Address.builder()
                                .roadAddress(request.getAddress())
                                .detailAddress(request.getDetailAddress())
                                .postCode(request.getPostcode())
                                .build())
                        .deliveryStatus(DeliveryStatus.DELIVERY_READY)
                        .deliveryMemo(request.getDeliveryMemo())
                        .recipient(request.getRecipient())
                        .recipientPhone(request.getRecipientPhone())
                        .build())
                .ordererInfo(OrdererInfo.builder()
                                .ordererName(request.getOrdererName())
                                .ordererPhone(request.getOrdererPhone())
                                .ordererEmail(request.getOrdererEmail())
                                .build())
                .build();

        List<OrderItem> orderItems = request.getOrderItems()
                .stream()
                .map(orderLineRequest -> {
                    // findItem 의 카테고리를 확인 해서 분기 처리가 필요하다.(카테고리 추가 후 로직 변경 필요.)
                    // clothes 상품인 경우.
                    Clothes clothes = clothesRepository.findById(orderLineRequest.getItemId()).orElseThrow(()
                            -> new ItemNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

                    ClothesSize clothesSize = clothesSizeRepository.getClothesSizebySizelabel(clothes, orderLineRequest.getSize());

                    return new OrderClothes(orderLineRequest.getOrderQuantity(), clothes, clothesSize);
                    // 그외의 상품인 경우.
                }).collect(Collectors.toList());

        order.changeOrderItems(orderItems);
        orderRepository.save(order);

        // 주문시, 마일리지 차감.
        if(request.getUsedMileage() != null && request.getUsedMileage() > 0){
            mileageService.deductMileage(order.getOrderNum(), member.getAuthId(), -request.getUsedMileage(), MileageContent.USED_MILEAGE_DEDUCTION);
        }

        return order.getOrderNum();
    }

    /**
     * 고객이 주문 취소하기 위한 메소드.(현재 로그인 한 회원의 주문 정보만 취소가 가능하다.)
     * @param orderNum 주문번호.
     * @param authId 현재 로그인 한 회원 아이디.
     * @return
     */
    @Transactional
    public Long cancelMyOrder(String orderNum, String authId){
        Order findOrder = orderRepository.findByOrderNumAndAuthId(orderNum, authId).orElseThrow(()
                -> new OrderNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

        findOrder.cancelOrder();

        mileageService.deleteMileage(findOrder.getOrderNum()); // 마일리지 삭제.

        return findOrder.getOrderId();
    }

    /**
     * 자신의 주문 리스트를 페이징하여 반환하기 위한 메소드.
     * (기본 페이징 사이즈는 10 입니다.)
     * @param authId 사용자 아이디.
     * @param pageable 페이지 정보.
     */
    public Page<Order> getMyOrderSummaryList(String authId, Pageable pageable){

        int page = (pageable.getPageNumber() == 0) ? 0 : (pageable.getPageNumber() - 1);
        pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "createdDate"));
        Page<Order> orderPage = orderRepository.getOrderListByAuthId(authId, pageable);

        return orderPage;
    }

    /**
     * 컨트롤러단에서 전달받은 Page<Order> 를 프론트단으로 전송 할 List<OrderSummaryDTO> 반환하기 위한 메소드.
     * @param orders 페이징 한 Order 정보.
     * @return List<OrderSummaryDTO>
     */
    public List<OrderSummaryDTO> getOrderSummaryDTO(List<Order> orders){
        return
                orders.stream()
                        .map(order -> {
                            String orderItemsName = "";
                            if(order.getOrderItems().size() > 1)
                                orderItemsName = order.getOrderItems().get(0).getItem().getName() + "외 " + (order.getOrderItems().size()-1) + "건";
                            else
                                orderItemsName = order.getOrderItems().get(0).getItem().getName();

                            return OrderSummaryDTO.builder()
                                    .orderId(order.getOrderId())
                                    .orderStatus(order.getOrderStatus().getDesc())
                                    .orderName(orderItemsName)
                                    .profileImgId(order.getOrderItems().get(0).getItem().getItemImages().get(0).getItemImageId())
                                    .createdDate(order.getCreatedDate())
                                    .build();
                        })
                        .collect(Collectors.toList());
    }

    /**
     * 주문 1건에 대한 상세 주문정보를 반환하는 메소드.
     * @param orderId 주문 도메인 id.
     * @return OrderDetailDTO
     */
    public OrderDetailDTO getOrderDetailDTO(Long orderId){
        Order findOrder = orderRepository.findById(orderId).orElseThrow(()
                -> new OrderNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

        // 주문 상품.
        List<OrderItemDTO> orderItemDTOS = findOrder.getOrderItems()
                .stream()
                .map(orderItem -> {
                    OrderItemDTO orderItemDTO = OrderItemDTO.builder()
                            .name(orderItem.getItem().getName())
                            .price(orderItem.getItem().getPrice())
                            .color(orderItem.getItem().getColor().getDesc())
                            .profileImg(orderItem.getItem().getItemImages().get(0).getItemImageId())
                            .orderQuantity(orderItem.getOrderQuantity())
                            .discountName("없음")
                            .discountPrice(0l)
                            .build();

                    // Clothes 상품만 사이즈 정보를 가지고 있기에(ClothesSize 로 관리)
                    // OrderClothes 인 경우를 체크하고 size 정보를 추가한다.
                    if(orderItem instanceof OrderClothes){
                        OrderClothes orderClothes = (OrderClothes) orderItem;
                        orderItemDTO.setSize(orderClothes.getClothesSize().getSizeLabel());
                    }

                    return orderItemDTO;
                })
                .collect(Collectors.toList());

        // 결제 정보.
        PaymentDTO payment = paymentService.getPamentDetail(findOrder.getPaymentNum());
        payment.setDeliveryAmount(findOrder.getDelivery().getDeliveryAmount()); // 배송비.

        // 마일리지 정보.
        MileageDTO mileage = mileageService.getMileageByOrderNum(findOrder.getOrderNum(), MileageContent.USED_MILEAGE_DEDUCTION);
        payment.setMileagePoint(mileage.getMileagePoint());

        // 할인 정보.

        OrderDetailDTO orderDetailDTO = OrderDetailDTO.builder()
                .orderId(findOrder.getOrderId())
                .orderDate(LocalDateTime.now())
                .orderItems(orderItemDTOS)
                .recipient(findOrder.getDelivery().getRecipient())
                .recipientPhone(findOrder.getDelivery().getRecipientPhone())
                .address(findOrder.getDelivery().getAddress().getRoadAddress() + findOrder.getDelivery().getAddress().getDetailAddress())
                .orderStatus(findOrder.getOrderStatus().getDesc())
                .ordererInfo(OrdererInfo.builder()
                        .ordererName(findOrder.getOrdererInfo().getOrdererName())
                        .ordererPhone(findOrder.getOrdererInfo().getOrdererPhone())
                        .ordererEmail(findOrder.getOrdererInfo().getOrdererEmail())
                        .build())
                .deliveryMemo(findOrder.getDelivery().getDeliveryMemo())
                .orderNum(findOrder.getOrderNum())
                .deliveryAmount(findOrder.getDelivery().getDeliveryAmount())
                .paymentInfo(payment)
                .build();

        return orderDetailDTO;
    }

    /**
     * 결제 유효성 검사 메소드.
     * 유효성 검사 이후, 결제 도메인(Payment) 객체를 생성 후 주문 도메인(Order) 주문상태를 변경한다(payOrder() method 참고).
     * 유효성 검사 내용
     * 1) 주문 금액과 결제 금액의 일치여부, 일치하지 않을 시 PaymentFailByValidatedAmountException throw
     * 2) 주문의 주문상태가 결제 가능한여부, 일치하지 않을 시 PaymentFailByValidatedOrderStatusException throw
     * @param paymentDTO 컨트롤러에서 전달 받은 Iamprot 조회 정보를 가지고 있는 DTO 객체.
     * @param authId 회원 아이디.
     * @return validate result
     */
    @Transactional
    public void validatePaymentByIamport(PaymentIamportDTO paymentDTO, String authId) {
        Order findOrder = orderRepository.findByOrderNumAndAuthId(authId, paymentDTO.getMerchantUid()).orElseThrow(()
                -> new PaymentFailException(ErrorCode.ORDER_NOT_FOUND));

        long orderITemsAmount = findOrder.getOrderItems()
                .stream()
                .mapToLong( orderItem -> orderItem.getItem().getPrice())
                .sum();

        // 배송비.
        long deliveryAmount = findOrder.getDelivery().getDeliveryAmount();

        // 마일리지.
        long mileagePoint = mileageService.getMileageByOrderNum(findOrder.getOrderNum(), MileageContent.USED_MILEAGE_DEDUCTION).getMileagePoint();

        // 총금액.
        long orderTotalAmount = orderITemsAmount + deliveryAmount - mileagePoint;

        // 주문 금액과 결제 총금액 확인
        if(paymentDTO.getPaymentAmount() != orderTotalAmount){
            throw new PaymentFailByValidatedAmountException(ErrorCode.PAYMENT_AMOUNT_IS_NOT_EQUAL_BY_ORDER_AMOUNT);
        }else{
            completeOrder(paymentDTO, authId);
        }
    }

    /**
     * 컨트롤러에서 전달 한 Iamport 결제 response dto 로 결제 도메인을 생성하고,
     * 주문 도메인의 주문상태를 '임시주문' -> '상품준비중(결제완료)' 으로 변경하는 메소드.
     * @param paymentDTO 컨트롤러에서 전달 받은 Iamprot 조회 정보를 가지고 있는 DTO 객체.
     * @param authId 로그인한 회원 아이디.
     * @return paymentId
     */
    @Transactional
    private Long completeOrder(PaymentIamportDTO paymentDTO, String authId) {
        Order findOrder = orderRepository.findByOrderNumAndAuthId(authId, paymentDTO.getMerchantUid()).orElseThrow(()
                -> new PaymentFailException(ErrorCode.ORDER_NOT_FOUND));

        // 주문 상태 변경.
        findOrder.payOrder(paymentDTO.getImpUid());

        // 결제 도메인.
        Payment payment = Payment.builder()
                .paymentNum(paymentDTO.getImpUid())
                .orderNum(paymentDTO.getMerchantUid())
                .payMethod(paymentDTO.getPayMethod())
                .payStatus(paymentDTO.getPayStatus())
                .payAmount(paymentDTO.getPaymentAmount())
                .orderName(paymentDTO.getName())
                .build();

        //결제시, 상품금액의 10퍼센트 마일리지 적립.
        mileageService.accumulateMileage(paymentDTO.getMerchantUid(), authId, (long)(paymentDTO.getPaymentAmount() * 0.1)
                , MileageContent.PAYMENT_MILEAGE_ACCUMULATE);

        paymentRepository.save(payment);

        return payment.getPaymentId();
    }

    /**
     * 결제에 대한 유효성 검사를 실패 한 경우, 실패한 결제건의 주문을 삭제하기 위한 메소드.
     * 현재 로그인한 회원의 아이디와 결제건에 대한 주문번호로 조회 된 '임시저장' 상태의 주문을 삭제합니다.
     * @param orderNum 주문 도메인의 주문번호.
     * @param authId 로그인한 회원의 아이디.
     */
    @Transactional
    public void deleteTempOrder(String orderNum, String authId){
        orderRepository.findByOrderNumAndAuthId(orderNum, authId).ifPresent(order -> {
            if(order.getOrderStatus() == OrderStatus.ORDER_TEMP){
                mileageService.deleteMileage(order.getOrderNum()); // 마일리지 삭제.
                order.cancelOrder(); // 상품 재고량 복구.
                orderRepository.delete(order);
            }else{
                log.error("결제 취소시, 임시상태 주문을 삭제하는데 실패하였습니다.");
                log.error("주문번호: " + orderNum + " 건은 삭제 가능한 주문 정보가 아닙니다.");
            }
        });

    }

    /**
     * 현재 로그인한 회원의 아이디의 '임시주문' 주문을 삭제하는 메소드.
     * deleteTempOrder()는 벡엔드단에서 결제처리가 실패 한 경우, '임시저장' 상태의 주문을 삭제하는데 사용되고,
     * deleteAllTempOrder()는 프론트단에서 결제처리가 실패 한 경우, '임시저장' 상태의 주문윽 삭제하는데 사용된다.
     * @param authId 로그인한 회원의 아이디.
     */
    @Transactional
    public void deleteAllTempOrder(String authId){
        List<Order> findTempOrders = orderRepository.getOrderIdsByAuthId(authId, OrderStatus.ORDER_TEMP);
        if(!findTempOrders.isEmpty()){
            for(Order order: findTempOrders){
                mileageService.deleteMileage(order.getOrderNum()); // 마일리지 삭제.
                order.cancelOrder(); // 상품 재고량 복구.
                orderRepository.delete(order);
            }
        }
    }
}
