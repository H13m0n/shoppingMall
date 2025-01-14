package com.allan.shoppingMall.domains.order.presentation;

import com.allan.shoppingMall.common.exception.ErrorCode;
import com.allan.shoppingMall.common.exception.order.payment.PaymentFailByValidatedAmountException;
import com.allan.shoppingMall.common.exception.order.payment.PaymentFailByValidatedOrderStatusException;
import com.allan.shoppingMall.common.exception.order.payment.PaymentFailException;
import com.allan.shoppingMall.common.exception.order.RefundFailException;
import com.allan.shoppingMall.domains.cart.service.CartService;
import com.allan.shoppingMall.domains.infra.AuthenticationConverter;
import com.allan.shoppingMall.domains.member.domain.Member;
import com.allan.shoppingMall.domains.order.domain.model.CompletdOrderInfo;
import com.allan.shoppingMall.domains.order.domain.model.OrderRequest;
import com.allan.shoppingMall.domains.order.service.OrderService;
import com.allan.shoppingMall.domains.payment.domain.model.PaymentRequest;
import com.allan.shoppingMall.domains.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RestOrderController.class
)
@AutoConfigureMockMvc
@WithMockUser
public class RestOrderControllerTest {

    @MockBean
    private AuthenticationConverter authenticationConverter;

    @MockBean
    private OrderService orderService;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private IamportClient iamportClient;

    @Autowired
    MockMvc mvc;

    // securityConfig.class 의 SignInSuccessHandler 에서 사용 할 cartService.
    @MockBean
    CartService cartService;

    @Test
    public void 주문_성공_테스트() throws Exception {
        //given
        Member TEST_MEMBER = Member
                .builder()
                .name("testName")
                .phone("010-0000-0000")
                .email("testEmail")
                .build();

        given(authenticationConverter.getMemberFromAuthentication(any()))
                .willReturn(TEST_MEMBER);

        String TEST_ORDER_NUM = "testOrderNum";

        given(orderService.order(any(), any()))
                .willReturn(TEST_ORDER_NUM);

        OrderRequest TEST_ORDER_REQUEST = new OrderRequest();
        TEST_ORDER_REQUEST.setOrdererName("테스터");
        TEST_ORDER_REQUEST.setOrdererPhone("01000000000");
        TEST_ORDER_REQUEST.setOrdererEmail("test@test.com");
        TEST_ORDER_REQUEST.setRecipientName("테스터");
        TEST_ORDER_REQUEST.setRecipientPhone("01011112222");
        TEST_ORDER_REQUEST.setPostcode("65044");
        TEST_ORDER_REQUEST.setAddress("testAddress");
        TEST_ORDER_REQUEST.setDetailAddress("testDetailAddress");


        //when
        ResultActions resultActions = mvc.perform(post("/order")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(asJsonString(TEST_ORDER_REQUEST)));

        //then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("apiResult").value(OrderResult.ORDER_SUCCESS.getResult()))
                .andExpect(jsonPath("apiResultMessage").value(OrderResult.ORDER_SUCCESS.getMessage()))
                .andExpect(jsonPath("orderNum").value(TEST_ORDER_NUM));
    }

    @Test
    public void 주문_결제정보_유효성검사_성공_테스트() throws Exception {
        //given
        Payment TEST_PAYMENT = createPayment();

        IamportResponse<Payment> TEST_IAMPORT_RESOURCE = new IamportResponse<Payment>();
        ReflectionTestUtils.setField(TEST_IAMPORT_RESOURCE, "response", TEST_PAYMENT);

        given(iamportClient.paymentByImpUid(any()))
                .willReturn(TEST_IAMPORT_RESOURCE);

        given(orderService.validatePaymentByIamport(any(), any()))
                .willReturn(new CompletdOrderInfo(1l, "testAuthId"));

        PaymentRequest TEST_PAYMENT_REQUEST = new PaymentRequest();
        TEST_PAYMENT_REQUEST.setImp_uid("test_imp_uid");
        TEST_PAYMENT_REQUEST.setMerchant_uid("test_merchantUid_uid");

        //when
        ResultActions resultActions = mvc.perform(post("/order/complete")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(asJsonString(TEST_PAYMENT_REQUEST)));

        //then
        verify(iamportClient, atLeastOnce()).paymentByImpUid(any());
        verify(orderService, atLeastOnce()).validatePaymentByIamport(any(), any());
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("apiResult").value(OrderResult.PAYMENT_SUCCESS.getResult()))
                .andExpect(jsonPath("apiResultMessage").value(OrderResult.PAYMENT_SUCCESS.getMessage()))
                .andExpect(jsonPath("orderNum").value(TEST_PAYMENT.getMerchantUid()));
    }

    @Test
    public void 환불_불가능한_주문상태로_인한_환불_실패_테스트() throws Exception {
        //given
        Payment TEST_PAYMENT = createPayment();

        IamportResponse<Payment> TEST_IAMPORT_RESOURCE = new IamportResponse<Payment>();
        ReflectionTestUtils.setField(TEST_IAMPORT_RESOURCE, "response", TEST_PAYMENT);

        given(iamportClient.paymentByImpUid(any()))
                .willReturn(TEST_IAMPORT_RESOURCE);

        PaymentFailException TEST_PAYMENT_FAIL_EXCEPTION = new PaymentFailException(ErrorCode.PAYMENT_AMOUNT_IS_NOT_EQUAL_BY_ORDER_AMOUNT);
        doThrow( TEST_PAYMENT_FAIL_EXCEPTION).when(orderService).validatePaymentByIamport(any(), any());

        RefundFailException TEST_REFUND_FAIL_EXCEPTION = new RefundFailException(ErrorCode.ORDER_REFUND_NOT_ALLOWED);
        doThrow(TEST_REFUND_FAIL_EXCEPTION).when(paymentService).refundPaymentForPaymentValidationFail(any(), any(), any());

        PaymentRequest TEST_PAYMENT_REQUEST = new PaymentRequest();
        TEST_PAYMENT_REQUEST.setImp_uid("test_imp_uid");
        TEST_PAYMENT_REQUEST.setMerchant_uid("test_merchantUid_uid");

        //when
        ResultActions resultActions = mvc.perform(post("/order/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(TEST_PAYMENT_REQUEST)));

        //then
        verify(orderService, atLeastOnce()).validatePaymentByIamport(any(), any());
        verify(paymentService, atLeastOnce()).refundPaymentForPaymentValidationFail(any(), any(), any());
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("apiResult").value(OrderResult.PAYMENT_REFUND_FAIL.getResult()))
                .andExpect(jsonPath("apiResultMessage").value(OrderResult.PAYMENT_REFUND_FAIL.getMessage()))
                .andExpect(jsonPath("orderNum").value("empty"))
                .andExpect(jsonPath("errorResponse.errorCode").value(TEST_REFUND_FAIL_EXCEPTION.getErrorCode().getCode()))
                .andExpect(jsonPath("errorResponse.status").value(TEST_REFUND_FAIL_EXCEPTION.getErrorCode().getStatus()))
                .andExpect(jsonPath("errorResponse.errMsg").value(TEST_REFUND_FAIL_EXCEPTION.getErrorCode().getMessage()));
    }

    @Test
    public void 결제금액과_주문금액_불일치로_인한_결제_실패_테스트() throws Exception {
        //given
        Payment TEST_PAYMENT = createPayment();

        IamportResponse<Payment> TEST_IAMPORT_RESOURCE = new IamportResponse<Payment>();
        ReflectionTestUtils.setField(TEST_IAMPORT_RESOURCE, "response", TEST_PAYMENT);

        given(iamportClient.paymentByImpUid(any()))
                .willReturn(TEST_IAMPORT_RESOURCE);

        PaymentFailByValidatedAmountException TEST_PAYMENT_FAIL_EXCEPTION = new PaymentFailByValidatedAmountException(ErrorCode.PAYMENT_AMOUNT_IS_NOT_EQUAL_BY_ORDER_AMOUNT);
        doThrow( TEST_PAYMENT_FAIL_EXCEPTION).when(orderService).validatePaymentByIamport(any(), any());

        String TEST_REFUND_PAYMENT_NUM = "test_payment_num";
        given(paymentService.refundPaymentForPaymentValidationFail(any(),any(), any()))
                .willReturn(TEST_REFUND_PAYMENT_NUM);

        PaymentRequest TEST_PAYMENT_REQUEST = new PaymentRequest();
        TEST_PAYMENT_REQUEST.setImp_uid("test_imp_uid");
        TEST_PAYMENT_REQUEST.setMerchant_uid("test_merchantUid_uid");

        //when
        ResultActions resultActions = mvc.perform(post("/order/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(TEST_PAYMENT_REQUEST)));

        //then
        verify(orderService, atLeastOnce()).validatePaymentByIamport(any(), any());
        verify(paymentService, atLeastOnce()).refundPaymentForPaymentValidationFail(any(), any(), any());
        verify(orderService, atLeastOnce()).deleteTempOrder(TEST_PAYMENT.getMerchantUid(), "user");
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("apiResult").value(OrderResult.PAYMENT_FAIL.getResult()))
                .andExpect(jsonPath("apiResultMessage").value(OrderResult.PAYMENT_FAIL.getMessage()))
                .andExpect(jsonPath("orderNum").value("empty"))
                .andExpect(jsonPath("errorResponse.errorCode").value(TEST_PAYMENT_FAIL_EXCEPTION.getErrorCode().getCode()))
                .andExpect(jsonPath("errorResponse.status").value(TEST_PAYMENT_FAIL_EXCEPTION.getErrorCode().getStatus()))
                .andExpect(jsonPath("errorResponse.errMsg").value(TEST_PAYMENT_FAIL_EXCEPTION.getErrorCode().getMessage()));

    }

    @Test
    public void 결제_불가능한_주문상태로_인한_결제_실패_테스트() throws Exception {
        //given
        Payment TEST_PAYMENT = createPayment();

        IamportResponse<Payment> TEST_IAMPORT_RESOURCE = new IamportResponse<Payment>();
        ReflectionTestUtils.setField(TEST_IAMPORT_RESOURCE, "response", TEST_PAYMENT);

        given(iamportClient.paymentByImpUid(any()))
                .willReturn(TEST_IAMPORT_RESOURCE);

        PaymentFailByValidatedOrderStatusException TEST_PAYMENT_FAIL_EXCEPTION = new PaymentFailByValidatedOrderStatusException(ErrorCode.PAYMENT_INVALID_ORDER_STATUS);
        doThrow( TEST_PAYMENT_FAIL_EXCEPTION).when(orderService).validatePaymentByIamport(any(), any());

        String TEST_REFUND_PAYMENT_NUM = "test_payment_num";
        given(paymentService.refundPaymentForPaymentValidationFail(any(),any(), any()))
                .willReturn(TEST_REFUND_PAYMENT_NUM);

        PaymentRequest TEST_PAYMENT_REQUEST = new PaymentRequest();
        TEST_PAYMENT_REQUEST.setImp_uid("test_imp_uid");
        TEST_PAYMENT_REQUEST.setMerchant_uid("test_merchantUid_uid");

        //when
        ResultActions resultActions = mvc.perform(post("/order/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(TEST_PAYMENT_REQUEST)));

        //then
        verify(orderService, atLeastOnce()).validatePaymentByIamport(any(), any());
        verify(paymentService, atLeastOnce()).refundPaymentForPaymentValidationFail(any(), any(), any());
        verify(orderService, atLeastOnce()).deleteTempOrder(TEST_PAYMENT.getMerchantUid(), "user");
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("apiResult").value(OrderResult.PAYMENT_FAIL.getResult()))
                .andExpect(jsonPath("apiResultMessage").value(OrderResult.PAYMENT_FAIL.getMessage()))
                .andExpect(jsonPath("orderNum").value("empty"))
                .andExpect(jsonPath("errorResponse.errorCode").value(TEST_PAYMENT_FAIL_EXCEPTION.getErrorCode().getCode()))
                .andExpect(jsonPath("errorResponse.status").value(TEST_PAYMENT_FAIL_EXCEPTION.getErrorCode().getStatus()))
                .andExpect(jsonPath("errorResponse.errMsg").value(TEST_PAYMENT_FAIL_EXCEPTION.getErrorCode().getMessage()));
    }

    private Payment createPayment(){
        Payment TEST_PAYMENT = new Payment();
        ReflectionTestUtils.setField(TEST_PAYMENT, "imp_uid", "test_imp_uid");
        ReflectionTestUtils.setField(TEST_PAYMENT, "merchant_uid", "test_merchant_uid");
        ReflectionTestUtils.setField(TEST_PAYMENT, "pay_method", "test_pay_method");
        ReflectionTestUtils.setField(TEST_PAYMENT, "status", "test_status");
        ReflectionTestUtils.setField(TEST_PAYMENT, "amount", new BigDecimal(1));

        return TEST_PAYMENT;
    }

    public static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
