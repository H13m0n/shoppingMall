package com.allan.shoppingMall.domains.item.domain.model;

import com.allan.shoppingMall.domains.item.domain.ImageType;
import com.allan.shoppingMall.domains.item.domain.ItemImage;
import com.allan.shoppingMall.domains.item.domain.clothes.ClothesDetail;
import com.allan.shoppingMall.domains.item.domain.clothes.ClothesFabric;
import com.allan.shoppingMall.domains.item.domain.clothes.ClothesSize;
import com.allan.shoppingMall.domains.item.domain.clothes.ModelSize;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class ClothesDTO {

    private Long clothesId;

    private String clothesName;

    private Long price;

    private Long stockQuantity;

    private String engName;

    private List<Long> previewImages = new ArrayList<>();

    private List<Long> detailImages = new ArrayList<>();

    private List<ModelSizeDTO> modelSizes = new ArrayList<>();

    private List<ClothesFabricDTO> clothesFabrics = new ArrayList<>();

    private List<ClothesDetailDTO> clothesDetails = new ArrayList<>();

    private List<ClothesSizeDTO> clothesSizes = new ArrayList<>();

    private List<ItemColorDTO> colors = new ArrayList<>();

    // 의류에 기타 정보를 담을 필드.
    private String etc;

    // 의상 상세 페이지에서 사용.
    private int clothesSizesCnt;
    private int clothesColorsCnt;

    @Builder
    public ClothesDTO(Long clothesId, String clothesName, Long price, Long stockQuantity, String engName, List<ItemImage> itemImages, List<ModelSizeDTO> modelSizes,
                      List<ClothesFabricDTO> clothesFabrics, List<ClothesDetailDTO> clothesDetails, List<ClothesSizeDTO> clothesSizes, String etc,
                      List<ItemColorDTO> colors, int clothesSizesCnt, int clothesColorsCnt) {
        this.clothesId = clothesId;
        this.clothesName = clothesName;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.engName = engName;
        this.modelSizes = modelSizes;
        this.clothesFabrics = clothesFabrics;
        this.clothesDetails = clothesDetails;
        this.clothesSizes = clothesSizes;
        this.etc = etc;
        setImgaes(itemImages);
        this.colors = colors;
        this.clothesSizesCnt = clothesSizesCnt;
        this.clothesColorsCnt = clothesColorsCnt;
    }

    /**
     * 단일 Clothes Entity 정보를 프론트단에서 요구하는 '미리보기', '세부' 사진 정보로
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
