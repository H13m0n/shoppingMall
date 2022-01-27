package com.allan.shoppingMall.domains.cart.domain;

import com.allan.shoppingMall.common.domain.BaseTimeEntity;
import com.allan.shoppingMall.domains.member.domain.Member;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 쇼핑몰 장바구니 도메인.
 * 장바구니 도메인은 비회원도 추가 해야 하기 때문에 작성자, 수정자 정보를 JpaAuditing 으로 처리하는데 무리가 있기에
 * BaseEntity 가 아닌, BaseTimeEntity를 상속하도록 함.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class Cart extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cartId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", unique = true)
    private Member member;

    // 비회원이 장바구니 생성시, 장바구니와 쿠키정보 연관관계를 위한 필드.
    @Column(name = "cookie_id", unique = true)
    private String ckId;

    @OneToMany(mappedBy = "cart", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    private List<CartItem> cartItems = new ArrayList<CartItem>();

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Cart 엔티티의 경우 초기 가입시 로그인 된 아이디를 사용하여 생성자와 수정자를 설정하는
     * JpaAuditing LoginIdAuditorAware 를 사용하는 불가능하기 때문에
     * 엔티티 최초 persist 시점에 생성하여 입력하도록 한다.
     */
    @PrePersist
    public void setUp(){
        this.createdBy = "system";
        this.updatedBy = "system";
    }

    public void addCartItems(List<CartItem> cartItems){
        for(CartItem cartItem : cartItems){
            if(this.cartItems.contains(cartItem)){
                log.info("cartItem is already existed in cart.");
                int cartItemIndex = this.cartItems.indexOf(cartItem);
                this.cartItems.get(cartItemIndex).addCartQuantity(cartItem.getCartQuantity());
            }else{
                cartItem.changeCart(this);
                this.cartItems.add(cartItem);
            }

        }
    }

    @Builder
    public Cart(Member member, String ckId) {
        this.member = member;
        this.ckId = ckId;
    }
}