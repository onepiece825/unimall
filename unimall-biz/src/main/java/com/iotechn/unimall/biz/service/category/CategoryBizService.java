package com.iotechn.unimall.biz.service.category;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iotechn.unimall.biz.constant.CacheConst;
import com.iotechn.unimall.core.Const;
import com.iotechn.unimall.core.exception.ServiceException;
import com.iotechn.unimall.data.annotaion.AspectCommonCache;
import com.iotechn.unimall.data.component.CacheComponent;
import com.iotechn.unimall.data.domain.CategoryDO;
import com.iotechn.unimall.data.dto.CategoryDTO;
import com.iotechn.unimall.data.dto.CategoryTreeNodeDTO;
import com.iotechn.unimall.data.mapper.CategoryMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by rize on 2019/7/12.
 */
@Service
public class CategoryBizService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private CacheComponent cacheComponent;

    /*获取一棵两级类目树*/
    @AspectCommonCache(value = CacheConst.CATEGORY_NODE_SECOND_LEVEL_TREE)
    public List<CategoryTreeNodeDTO> categorySecondLevelTree() throws ServiceException {
        List<CategoryDO> categoryDOS = categoryMapper.selectList(new QueryWrapper<CategoryDO>().le("level",1 ).orderByAsc("level"));
        List<CategoryTreeNodeDTO> list = categoryDOS.stream().filter((item) -> (item.getParentId().equals(0l))).map(item -> {
            CategoryTreeNodeDTO dto = new CategoryTreeNodeDTO();
            dto.setLabel(item.getTitle());
            dto.setLevel(0);
            dto.setFullName(dto.getLabel());
            dto.setValue(item.getId());
            dto.setChildren(new LinkedList<>());
            return dto;
        }).collect(Collectors.toList());
        list.forEach(item -> {
            categoryDOS.forEach(categoryDO -> {
                if (categoryDO.getParentId().equals(item.getValue())) {
                    CategoryTreeNodeDTO categoryTreeNodeDTO = new CategoryTreeNodeDTO();
                    categoryTreeNodeDTO.setChildren(new LinkedList<>());
                    categoryTreeNodeDTO.setValue(categoryDO.getId());
                    categoryTreeNodeDTO.setLabel(categoryDO.getTitle());
                    categoryTreeNodeDTO.setLevel(1);
                    categoryTreeNodeDTO.setParent(item.getValue());
                    categoryTreeNodeDTO.setFullName(item.getFullName() +"/"+ categoryDO.getTitle());
                    item.getChildren().add(categoryTreeNodeDTO);
                }
            });
        });
        return list;
    }

    /**
     * 获取一棵三级类目树,类内部有调用，因此不能切面
     * */
    public List<CategoryDTO> categoryList() throws ServiceException {
        List<CategoryDTO> categoryDTOListFormCache = cacheComponent.getObjList(CacheConst.CATEGORY_DTO_THREE_LEVEL_TREE, CategoryDTO.class);
        if (categoryDTOListFormCache != null) {
            return categoryDTOListFormCache;
        }
        //从数据库查询
        List<CategoryDO> categoryDOList = categoryMapper.selectList(new QueryWrapper<>());
        //组装DTO
        List<CategoryDTO> categoryDTOList = new LinkedList<>();
        categoryDOList.forEach(categoryDO -> {
            if (categoryDO.getParentId() == 0) {
                CategoryDTO categoryDTO = new CategoryDTO();
                BeanUtils.copyProperties(categoryDO, categoryDTO);
                categoryDTOList.add(categoryDTO);
            }
        });

        //遍历二、三级
        categoryDTOList.forEach(categoryDTO -> {
            categoryDOList.forEach(categoryDO -> {
                if (categoryDO.getParentId().equals(categoryDTO.getId())) {
                    List<CategoryDTO> childrenList = categoryDTO.getChildrenList();
                    if (childrenList == null) {
                        childrenList = new LinkedList<>();
                        categoryDTO.setChildrenList(childrenList);
                    }
                    CategoryDTO childCategoryDTO = new CategoryDTO();
                    BeanUtils.copyProperties(categoryDO, childCategoryDTO);
                    childCategoryDTO.setChildrenList(new LinkedList<>());
                    childrenList.add(childCategoryDTO);
                    categoryDOList.forEach(leaf -> {
                        if (childCategoryDTO.getId().equals(leaf.getParentId())) {
                            CategoryDTO leafCategoryDTO = new CategoryDTO();
                            BeanUtils.copyProperties(leaf, leafCategoryDTO);
                            childCategoryDTO.getChildrenList().add(leafCategoryDTO);
                        }
                    });
                }
            });
        });
        //放入缓存
        cacheComponent.putObj(CacheConst.CATEGORY_DTO_THREE_LEVEL_TREE, categoryDTOList, Const.CACHE_ONE_DAY);
        return categoryDTOList;
    }

    /**
     * 将 叶子类目 的 Key 做为键 parentId_parent.parentId 作为值放入一个hash表中。查询familyId直接查一次hash表即可查出来。Hash表Size就等于叶子类目数量
     *
     * @param categoryId 仅可传入叶子节点
     * @return
     * @throws ServiceException
     */
    public List<Long> getCategoryFamily(Long categoryId) throws ServiceException {
        Map<String, String> hashAll = cacheComponent.getHashAll(CacheConst.CATEGORY_ID_HASH);
        if (hashAll == null) {
            //构建此Hash表
            final Map<String,String> newHash = new HashMap<>();
            //将所有子节点查询出来
            List<CategoryDTO> categoryDTOList = categoryList();
            categoryDTOList.forEach(topItem -> {
                if (!CollectionUtils.isEmpty(topItem.getChildrenList()))
                    topItem.getChildrenList().forEach(subItem -> {
                        if (!CollectionUtils.isEmpty(subItem.getChildrenList()))
                            subItem.getChildrenList().forEach(leafItem -> {
                                newHash.put("S" + leafItem.getId(), subItem.getId() + "_" + topItem.getId());
                            });
                    });
            });
            hashAll = newHash;
            cacheComponent.putHashAll(CacheConst.CATEGORY_ID_HASH, hashAll, Const.CACHE_ONE_DAY);
        }

        LinkedList<Long> ids = new LinkedList<>();
        ids.add(categoryId);
        String str = hashAll.get("S" + categoryId);
        if (!StringUtils.isEmpty(str)) {
            String[] split = str.split("_");
            ids.add(new Long(split[0]));
            ids.add(new Long(split[1]));
        }
        return ids;
    }

    /**
     * 将一父节点传入，返回父节点本身与其子节点，及其孙节点ID列表
     * @param categoryId
     * @return
     * @throws ServiceException
     */
    public List<Long> getCategorySelfAndChildren(Long categoryId) throws ServiceException {
        // TODO
        return Arrays.asList(categoryId);
    }

}
