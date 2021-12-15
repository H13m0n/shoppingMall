package com.allan.shoppingMall.domains.order.presentation;

import com.allan.shoppingMall.domains.infra.AuthenticationConverter;
import com.allan.shoppingMall.domains.member.domain.Member;
import com.allan.shoppingMall.domains.order.domain.model.OrderRequest;
import com.allan.shoppingMall.domains.order.domain.model.OrderSummaryRequest;
import com.allan.shoppingMall.domains.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = OrderController.class
)
@AutoConfigureMockMvc
@WithMockUser
public class OrderControllerTest {

    @MockBean
    AuthenticationConverter authenticationConverter;

    @MockBean
    OrderService orderService;

    @Autowired
    MockMvc mvc;

    @Test
    public void 주문_폼_테스트() throws Exception {
        //given
        OrderSummaryRequest TEST_ORDER_SUMMARY_REQUEST = new OrderSummaryRequest();
        Member TEST_MEMBER = Member.builder().build();
        given(authenticationConverter.getMemberFromAuthentication(any()))
                .willReturn(TEST_MEMBER);

        //when
        ResultActions resultActions = mvc.perform(get("/order/orderForm")
                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                            .flashAttr("orderSummaryRequst", TEST_ORDER_SUMMARY_REQUEST));

        //then
        verify(authenticationConverter, atLeastOnce()).getMemberFromAuthentication(any());
        resultActions
                .andExpect(model().attributeExists("orderInfo"))
                .andExpect(model().attributeExists("userInfo"))
                .andExpect(view().name("order/orderForm"))
                .andExpect(status().isOk());
    }

    @Test
    public void 주문_테스트() throws Exception {
        //given
        OrderRequest TEST_ORDER_REQUEST = new OrderRequest();
        Member TEST_MEMBER = Member.builder()
                .name("testName")
                .phone("0101-2020")
                .email("testEmail@email.com")
                .build();
        given(authenticationConverter.getMemberFromAuthentication(any()))
                .willReturn(TEST_MEMBER);

        //when
        ResultActions resultActions = mvc.perform(post("/order/save")
                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                            .flashAttr("orderRequest", TEST_ORDER_REQUEST));

        //then
        verify(authenticationConverter, atLeastOnce()).getMemberFromAuthentication(any());
        verify(orderService, atLeastOnce()).order(any(), any());
        assertThat(TEST_ORDER_REQUEST.getOrdererName(), is(TEST_MEMBER.getName()));
        assertThat(TEST_ORDER_REQUEST.getOrdererEmail(), is(TEST_MEMBER.getEmail()));
        assertThat(TEST_ORDER_REQUEST.getOrdererPhone(), is(TEST_MEMBER.getPhone()));
        resultActions
                .andExpect(status().is3xxRedirection());
    }

    @Test
    public void 주문결과_테스트() throws Exception {
        //given, when
        ResultActions resultActions = mvc.perform(get("/order/orderResult"));

        //then
        resultActions
                .andExpect(view().name("order/orderResult"));
    }
}