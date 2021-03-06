package com.mossle.cms.web;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import javax.servlet.http.HttpServletResponse;

import com.mossle.cms.CmsConstants;
import com.mossle.cms.domain.CmsArticle;
import com.mossle.cms.domain.CmsAttachment;
import com.mossle.cms.domain.CmsCatalog;
import com.mossle.cms.manager.CmsArticleManager;
import com.mossle.cms.manager.CmsAttachmentManager;
import com.mossle.cms.manager.CmsCatalogManager;
import com.mossle.cms.service.RenderService;

import com.mossle.core.hibernate.PropertyFilter;
import com.mossle.core.mapper.BeanMapper;
import com.mossle.core.mapper.JsonMapper;
import com.mossle.core.page.Page;
import com.mossle.core.spring.MessageHelper;

import com.mossle.ext.auth.CurrentUserHolder;
import com.mossle.ext.export.Exportor;
import com.mossle.ext.export.TableModel;
import com.mossle.ext.store.MultipartFileResource;
import com.mossle.ext.store.StoreConnector;
import com.mossle.ext.store.StoreDTO;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cms")
public class CmsArticleController {
    private CmsArticleManager cmsArticleManager;
    private CmsCatalogManager cmsCatalogManager;
    private CmsAttachmentManager cmsAttachmentManager;
    private Exportor exportor;
    private BeanMapper beanMapper = new BeanMapper();
    private MessageHelper messageHelper;
    private RenderService renderService;
    private StoreConnector storeConnector;
    private JsonMapper jsonMapper = new JsonMapper();
    private CurrentUserHolder currentUserHolder;

    @RequestMapping("cms-article-list")
    public String list(@ModelAttribute Page page,
            @RequestParam Map<String, Object> parameterMap, Model model) {
        List<PropertyFilter> propertyFilters = PropertyFilter
                .buildFromMap(parameterMap);
        page = cmsArticleManager.pagedQuery(page, propertyFilters);
        model.addAttribute("page", page);

        return "cms/cms-article-list";
    }

    @RequestMapping("cms-article-input")
    public String input(@RequestParam(value = "id", required = false) Long id,
            Model model) {
        if (id != null) {
            CmsArticle cmsArticle = cmsArticleManager.get(id);
            model.addAttribute("model", cmsArticle);
        }

        model.addAttribute("cmsCatalogs", cmsCatalogManager.getAll());

        return "cms/cms-article-input";
    }

    @RequestMapping("cms-article-save")
    public String save(@ModelAttribute CmsArticle cmsArticle,
            @RequestParam("cmsCatalogId") Long cmsCatalogId,
            RedirectAttributes redirectAttributes) {
        Long id = cmsArticle.getId();
        CmsArticle dest = null;

        if (id != null) {
            dest = cmsArticleManager.get(id);
            beanMapper.copy(cmsArticle, dest);
        } else {
            dest = cmsArticle;
        }

        if (id == null) {
            dest.setUserId(currentUserHolder.getUserId());
            dest.setCreateTime(new Date());
        }

        dest.setCmsCatalog(cmsCatalogManager.get(cmsCatalogId));

        cmsArticleManager.save(dest);
        messageHelper.addFlashMessage(redirectAttributes, "core.success.save",
                "保存成功");

        return "redirect:/cms/cms-article-list.do";
    }

    @RequestMapping("cms-article-remove")
    public String remove(@RequestParam("selectedItem") List<Long> selectedItem,
            RedirectAttributes redirectAttributes) {
        List<CmsArticle> cmsArticles = cmsArticleManager
                .findByIds(selectedItem);
        cmsArticleManager.removeAll(cmsArticles);
        messageHelper.addFlashMessage(redirectAttributes,
                "core.success.delete", "删除成功");

        return "redirect:/cms/cms-article-list.do";
    }

    @RequestMapping("cms-article-export")
    public void export(@ModelAttribute Page page,
            @RequestParam Map<String, Object> parameterMap,
            HttpServletResponse response) throws Exception {
        List<PropertyFilter> propertyFilters = PropertyFilter
                .buildFromMap(parameterMap);
        page = cmsArticleManager.pagedQuery(page, propertyFilters);

        List<CmsArticle> cmsArticles = (List<CmsArticle>) page.getResult();

        TableModel tableModel = new TableModel();
        tableModel.setName("cmsArticle");
        tableModel.addHeaders("id", "name");
        tableModel.setData(cmsArticles);
        exportor.export(response, tableModel);
    }

    @RequestMapping("cms-article-checkName")
    @ResponseBody
    public boolean checkName(@RequestParam("name") String name,
            @RequestParam(value = "id", required = false) Long id)
            throws Exception {
        String hql = "from CmsArticle where name=?";
        Object[] params = { name };

        if (id != null) {
            hql = "from CmsArticle where name=? and id<>?";
            params = new Object[] { name, id };
        }

        CmsArticle cmsArticle = cmsArticleManager.findUnique(hql, params);

        boolean result = (cmsArticle == null);

        return result;
    }

    @RequestMapping("cms-article-publish")
    public String publish(@RequestParam("id") Long id) {
        CmsArticle cmsArticle = cmsArticleManager.get(id);
        cmsArticle.setPublishTime(new Date());
        cmsArticle.setStatus(1);
        renderService.render(cmsArticle);

        return "redirect:/cms/cms-article-list.do";
    }

    @RequestMapping("cms-article-view")
    public String view(@RequestParam("id") Long id, Model model) {
        CmsArticle cmsArticle = cmsArticleManager.get(id);
        String html = renderService.view(cmsArticle);

        model.addAttribute("html", html);

        return "cms/cms-article-view";
    }

    @RequestMapping("cms-article-uploadImage")
    @ResponseBody
    public String uploadImage(@RequestParam("CKEditorFuncNum") String callback,
            @RequestParam("upload") MultipartFile attachment) throws Exception {
        StoreDTO storeDto = storeConnector.save("cms/html/r/images",
                new MultipartFileResource(attachment),
                attachment.getOriginalFilename());

        return "<script type='text/javascript'>"
                + "window.parent.CKEDITOR.tools.callFunction(" + callback
                + ",'" + "r/images/" + storeDto.getKey() + "','')"
                + "</script>";
    }

    @RequestMapping("cms-article-download")
    @ResponseBody
    public String download(@RequestParam("id") Long id) throws Exception {
        List<CmsAttachment> cmsAttachments = cmsAttachmentManager.findBy(
                "cmsArticle.id", id);

        Map<String, Object> data = new HashMap<String, Object>();
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        data.put("files", files);

        for (CmsAttachment cmsAttachment : cmsAttachments) {
            Map<String, Object> map = new HashMap<String, Object>();
            files.add(map);
            map.put("name", cmsAttachment.getName());
            map.put("url", "../rs/cms/image?key=" + cmsAttachment.getPath());

            // map.put("thumbnailUrl", "./rs/cms/image?key=" + storeDto.getKey());
        }

        return jsonMapper.toJson(data);
    }

    @RequestMapping("cms-article-upload")
    @ResponseBody
    public String upload(@RequestParam("id") Long id,
            @RequestParam("files[]") MultipartFile attachment) throws Exception {
        StoreDTO storeDto = storeConnector.save("cms/html/r/image",
                new MultipartFileResource(attachment),
                attachment.getOriginalFilename());
        CmsArticle cmsArticle = cmsArticleManager.get(id);
        CmsAttachment cmsAttachment = new CmsAttachment();
        cmsAttachment.setCmsArticle(cmsArticle);
        cmsAttachment.setName(attachment.getOriginalFilename());
        cmsAttachment.setPath(storeDto.getKey());
        cmsAttachmentManager.save(cmsAttachment);

        Map<String, Object> data = new HashMap<String, Object>();
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        data.put("files", files);

        Map<String, Object> map = new HashMap<String, Object>();
        files.add(map);
        map.put("name", attachment.getOriginalFilename());
        map.put("url", "../rs/cms/image?key=" + storeDto.getKey());

        // map.put("thumbnailUrl", "./rs/cms/image?key=" + storeDto.getKey());
        return jsonMapper.toJson(data);
    }

    @RequestMapping("cms-article-image")
    public String imageArticle(
            @RequestParam(value = "id", required = false) Long id, Model model) {
        if (id == null) {
            CmsArticle cmsArticle = new CmsArticle();
            cmsArticle.setType(CmsConstants.TYPE_IMAGE);
            cmsArticleManager.save(cmsArticle);
            model.addAttribute("model", cmsArticle);
        } else {
            CmsArticle cmsArticle = cmsArticleManager.get(id);
            model.addAttribute("model", cmsArticle);
        }

        model.addAttribute("cmsCatalogs", cmsCatalogManager.getAll());

        return "cms/cms-article-image";
    }

    @RequestMapping("cms-article-audio")
    public String audioArticle(
            @RequestParam(value = "id", required = false) Long id, Model model) {
        if (id == null) {
            CmsArticle cmsArticle = new CmsArticle();
            cmsArticle.setType(CmsConstants.TYPE_AUDIO);
            cmsArticleManager.save(cmsArticle);
            model.addAttribute("model", cmsArticle);
        } else {
            CmsArticle cmsArticle = cmsArticleManager.get(id);
            model.addAttribute("model", cmsArticle);
        }

        model.addAttribute("cmsCatalogs", cmsCatalogManager.getAll());

        return "cms/cms-article-audio";
    }

    @RequestMapping("cms-article-video")
    public String videoArticle(
            @RequestParam(value = "id", required = false) Long id, Model model) {
        if (id == null) {
            CmsArticle cmsArticle = new CmsArticle();
            cmsArticle.setType(CmsConstants.TYPE_VIDEO);
            cmsArticleManager.save(cmsArticle);
            model.addAttribute("model", cmsArticle);
        } else {
            CmsArticle cmsArticle = cmsArticleManager.get(id);
            model.addAttribute("model", cmsArticle);
        }

        model.addAttribute("cmsCatalogs", cmsCatalogManager.getAll());

        return "cms/cms-article-video";
    }

    // ~ ======================================================================
    @Resource
    public void setCmsArticleManager(CmsArticleManager cmsArticleManager) {
        this.cmsArticleManager = cmsArticleManager;
    }

    @Resource
    public void setCmsCatalogManager(CmsCatalogManager cmsCatalogManager) {
        this.cmsCatalogManager = cmsCatalogManager;
    }

    @Resource
    public void setCmsAttachmentManager(
            CmsAttachmentManager cmsAttachmentManager) {
        this.cmsAttachmentManager = cmsAttachmentManager;
    }

    @Resource
    public void setExportor(Exportor exportor) {
        this.exportor = exportor;
    }

    @Resource
    public void setMessageHelper(MessageHelper messageHelper) {
        this.messageHelper = messageHelper;
    }

    @Resource
    public void setRenderService(RenderService renderService) {
        this.renderService = renderService;
    }

    @Resource
    public void setStoreConnector(StoreConnector storeConnector) {
        this.storeConnector = storeConnector;
    }

    @Resource
    public void setCurrentUserHolder(CurrentUserHolder currentUserHolder) {
        this.currentUserHolder = currentUserHolder;
    }
}
