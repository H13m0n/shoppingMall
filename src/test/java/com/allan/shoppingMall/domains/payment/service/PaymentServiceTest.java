package com.allan.shoppingMall.domains.payment.service;

import com.allan.shoppingMall.common.exception.ErrorCode;
import com.allan.shoppingMall.domains.order.service.OrderService;
import com.allan.shoppingMall.domains.payment.domain.Payment;
import com.allan.shoppingMall.domains.payment.domain.PaymentRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.request.CancelData;
import com.siot.IamportRestClient.response.IamportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Rollback(value = true)
public class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    IamportClient client;

    PaymentService paymentService;

    @BeforeEach
    public void setUp(){
        MockitoAnnotations.openMocks(this);
        paymentService = new PaymentService(paymentRepository, client);
    }

    @Test
    public void 결제실패로인한_환불처리_테스트() throws Exception {
        //given
        Payment TEST_PAYMENT = Payment.builder()
                .payMethod("테스트결제방식")
                .payStatus("테스트결제상태")
                .paymentNum("test_impUid")
                .orderNum("test_merchantUid")
                .payAmount(1000l)
                .build();

        com.siot.IamportRestClient.response.Payment TEST_CANCEL_PAYMENT = new com.siot.IamportRestClient.response.Payment();
        ReflectionTestUtils.setField(TEST_CANCEL_PAYMENT, "merchant_uid", "testMerchantUid");

        IamportResponse<com.siot.IamportRestClient.response.Payment> TEST_IAMPORT_RESPONSE = new IamportResponse<com.siot.IamportRestClient.response.Payment>();
        ReflectionTestUtils.setField(TEST_IAMPORT_RESPONSE, "response", TEST_CANCEL_PAYMENT);
        ReflectionTestUtils.setField(TEST_IAMPORT_RESPONSE, "code", 3);
        ReflectionTestUtils.setField(TEST_IAMPORT_RESPONSE, "message", "testMessage");

        given(client.cancelPaymentByImpUid(any(CancelData.class)))
                .willReturn(TEST_IAMPORT_RESPONSE);

        Long TEST_DELETED_ORDER_ID = 1l;

        //when
        paymentService.refundPaymentAll(TEST_PAYMENT.getPaymentNum(), TEST_PAYMENT.getPayAmount(), ErrorCode.PAYMENT_AMOUNT_IS_NOT_EQUAL_BY_ORDER_AMOUNT, any());

        //then
        verify(client, atLeastOnce()).cancelPaymentByImpUid(any());
    }
}