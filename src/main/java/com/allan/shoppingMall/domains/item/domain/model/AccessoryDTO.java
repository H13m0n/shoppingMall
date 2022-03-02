package com.allan.shoppingMall.domains.item.domain.model;

import com.allan.shoppingMall.domains.item.domain.item.ImageType;
import com.allan.shoppingMall.domains.item.domain.item.ItemImage;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Accessory 도메인 정보 Object.
 * 프론트단에서 사용 할 악세서리 상품 정보를 담고 있습니다.
 */
@Getter
@Setter
public class AccessoryDTO {

    private Long accessoryId;

    private String accessoryName;

    private Long price;

    private String engName;

    private List<Long> previewImages = new ArrayList<>();

    private List<Long> detailImages = new ArrayList<>();

    private List<ItemFabricDTO> itemFabrics = new ArrayList<>();

    private List<ItemDetailDTO> itemDetails = new ArrayList<>();

    private List<ItemSizeDTO> itemSizes = new ArrayList<>();

    private String color;

    private Long categoryId;

    @Builder
    public AccessoryDTO(Long accessoryId, String accessoryName, Long price, String engName, List<ItemImage> itemImages,
                      List<ItemFabricDTO> itemFabrics, List<ItemDetailDTO> itemDetails, List<ItemSizeDTO> itemSizes,
                      String color, Long categoryId) {
        this.accessoryId = accessoryId;
        this.accessoryName = accessoryName;
        this.price = price;
        this.engName = engName;
        this.itemFabrics = itemFabrics;
        this.itemDetails = itemDetails;
        this.itemSizes = itemSizes;
        setImgaes(itemImages);
        this.color = color;
        this.categoryId = categoryId;
    }

    /**
     * 단일 Accessory Entity 정보를 프론트단에서 요구하는 '미리보기', '세부' 사진 정보로
     * 나누어 set 하는 함수.
     * @param itemImages
     */
    private void setImgaes(List<ItemImage> itemImages){
        List<Long> previewImages = itemImages
                .stream()
                .filter(itemImage ->
                        itemImage.getImageType().getWhereToUse().equals(ImageType.PREVIEW.getWhereToUse())
                ).map(itemImage -> {
                    return itemImage.getItemImageId();
                })
                .collect(Collectors.toList());

        List<Long> detailImages = itemImages
                .stream()
                .filter(itemImage -> itemImage.getImageType().getWhereToUse().equals(ImageType.PRODUCT.getWhereToUse()))
                .map(itemImage -> {
                    return itemImage.getItemImageId();
                })
                .collect(Collectors.toList());

        this.previewImages = previewImages;
        this.detailImages = detailImages;
    }
}