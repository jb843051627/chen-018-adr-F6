package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.auto.TAdrTermTaxon;
import com.fc.v2.model.custom.adr.CardOpenReceiptVo;
import com.fc.v2.model.custom.adr.TermSearchHitVo;
import com.fc.v2.service.ITAdrTermCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.List;

/**
 * 术语分类卡 Controller。
 *
 * 立卡规矩、三态步子、监督层级、本级名录占数、术语树检索——屏上只递话、只读数，
 * 定夺全在 service：这一屏摆的字与 service 交回来的必须是同一回算出的。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "术语分类卡")
@Controller
@RequestMapping("/AdrTermCardController")
public class AdrTermCardController extends BaseController {

    private final String prefix = "admin/adrTermCard";

    @Autowired
    private ITAdrTermCardService adrTermCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("adr:adrTermCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "术语分类卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "按条件查询，不私自添一档")
    @GetMapping("/list")
    @RequiresPermissions("adr:adrTermCard:list")
    @ResponseBody
    public ResultTable list(TAdrTermCard record) {
        QueryWrapper<TAdrTermCard> queryWrapper = new QueryWrapper<>();
        if (record.getOwnLevel() != null) {
            queryWrapper.eq("own_level", record.getOwnLevel());
        }
        if (record.getSiteNo() != null && !record.getSiteNo().isEmpty()) {
            queryWrapper.eq("site_no", record.getSiteNo());
        }
        queryWrapper.eq("version_flag", 0).eq("del_flag", 0).orderByAsc("id");
        startPage();
        com.github.pagehelper.PageInfo<TAdrTermCard> page =
                new com.github.pagehelper.PageInfo<>(adrTermCardService.selectTAdrTermCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "术语分类卡新增(旧门)", action = "add")
    @ApiOperation(value = "新增(旧门,签名留着)", notes = "内部已改走新规矩，不进库回0，缘故走/open看指名回执")
    @PostMapping("/add")
    @RequiresPermissions("adr:adrTermCard:add")
    @ResponseBody
    public AjaxResult add(TAdrTermCard record) {
        return toAjax(adrTermCardService.insertTAdrTermCard(record));
    }

    @Log(title = "术语分类卡立卡", action = "open")
    @ApiOperation(value = "立卡(新走法)", notes = "卡号/版次系统发；缺栏回执指名到栏；监督层级照三界线折")
    @PostMapping("/open")
    @RequiresPermissions("adr:adrTermCard:add")
    @ResponseBody
    public AjaxResult open(TAdrTermCard record, Integer ownLevel) {
        CardOpenReceiptVo receipt = adrTermCardService.insertTAdrTermCard(
                record, ownLevel == null ? 3 : ownLevel);
        return receipt.isAccepted()
                ? AjaxResult.success(200, receipt.getMessage(), receipt)
                : AjaxResult.error(500, receipt.getMessage()).put("missing", receipt.getMissingFields());
    }

    @ApiOperation(value = "当场点必填栏", notes = "按分类层级点出非写不可/数不像话的栏，空单即齐")
    @GetMapping("/pointMissing")
    @RequiresPermissions("adr:adrTermCard:add")
    @ResponseBody
    public AjaxResult pointMissing(TAdrTermCard record) {
        List<CardOpenReceiptVo.Missing> missing = adrTermCardService.pointMissing(record);
        return AjaxResult.successData(200, missing);
    }

    @Log(title = "年报告份数增减", action = "changeQty")
    @ApiOperation(value = "份数增减", notes = "三界线当场重折监督层级；零/负不收")
    @PostMapping("/changeQty")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult changeQty(Long id, BigDecimal qty) {
        try {
            TAdrTermCard card = adrTermCardService.changeQty(id, qty);
            return card == null ? AjaxResult.error("这张卡已冻住或查无，份数动不了")
                    : AjaxResult.success(200, "份数已改，监督层级重折为第" + card.getGradeLevel() + "层", card);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "改挂靠底册", action = "changeSite")
    @ApiOperation(value = "换挂靠底册", notes = "只认底册代号；照新底册三界线重折")
    @PostMapping("/changeSite")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult changeSite(Long id, String siteNo) {
        try {
            TAdrTermCard card = adrTermCardService.changeSite(id, siteNo);
            return card == null ? AjaxResult.error("这张卡已冻住或查无，挂靠动不了")
                    : AjaxResult.success(200, "已改挂" + siteNo + "，监督层级重折为第" + card.getGradeLevel() + "层", card);
        } catch (RuntimeException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "术语分类卡改版", action = "revise")
    @ApiOperation(value = "改版", notes = "新版一立旧版入历史；版次系统发；校验串随版重算")
    @PostMapping("/revise")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult revise(Long id, TAdrTermCard changes) {
        try {
            TAdrTermCard next = adrTermCardService.revise(id, changes);
            return next == null ? AjaxResult.error("冻住的卡要动只能够另开一张，不走改版")
                    : AjaxResult.success(200, "新版已立（第" + next.getNodeNo() + "版），旧版挪入历史层", next);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "分类卡往前迈一步", action = "advance")
    @ApiOperation(value = "往前迈一步", notes = "待核对→已核对→已冻住；步子不许乱迈")
    @PostMapping("/advance")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult advance(Long id) {
        TAdrTermCard card = adrTermCardService.advance(id);
        if (card == null) {
            return AjaxResult.error("迈不得：这张卡已冻住、查无，或不是现行那版");
        }
        String msg = card.getStatus() == 2 ? "已冻住：这张走完了，名额空出，要动只能另开一张"
                : "已核对：本版封住，校验串已随版落定";
        return AjaxResult.success(200, msg, card);
    }

    @Log(title = "分类卡退回一步", action = "rollback")
    @ApiOperation(value = "退回一步", notes = "只已核对退得回待核对；先前那趟留痕不毁")
    @PostMapping("/rollback")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult rollback(Long id) {
        TAdrTermCard card = adrTermCardService.rollback(id);
        if (card == null) {
            return AjaxResult.error("退不得：还没核对无步可退，冻住之后撤不下来");
        }
        return AjaxResult.success(200, "已退回待核对，先前封的那串照旧留着", card);
    }

    @ApiOperation(value = "当下走到哪一步", notes = "系统依已走完级数给，屏上录的不算")
    @GetMapping("/state")
    @RequiresPermissions("adr:adrTermCard:view")
    @ResponseBody
    public AjaxResult state(Long id) {
        TAdrTermCard card = adrTermCardService.currentState(id);
        return card == null ? AjaxResult.error("查无此卡") : AjaxResult.successData(200, card);
    }

    @ApiOperation(value = "术语树登记", notes = "术语编码系统发；层级一层套一层")
    @PostMapping("/taxon/register")
    @RequiresPermissions("adr:adrTermCard:add")
    @ResponseBody
    public AjaxResult registerTaxon(TAdrTermTaxon taxon) {
        try {
            return AjaxResult.success(200, "术语已登记，编码由系统发给", adrTermCardService.registerTaxon(taxon));
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @ApiOperation(value = "分类树检索", notes = "术语名号/症状词/上位分类码三处开搜，前缀与模糊都给；只摆本级")
    @GetMapping("/search")
    @RequiresPermissions("adr:adrTermCard:view")
    @ResponseBody
    public AjaxResult search(String keyword, Integer ownLevel) {
        List<TermSearchHitVo> hits = adrTermCardService.searchTerms(keyword, ownLevel == null ? 3 : ownLevel);
        return AjaxResult.successData(200, hits);
    }

    @ApiOperation(value = "本级名录一屏", notes = "占数与屏上卡同一回点算；只摆现行卡，不补空行")
    @GetMapping("/roster")
    @RequiresPermissions("adr:adrTermCard:view")
    @ResponseBody
    public AjaxResult roster(Integer ownLevel) {
        return AjaxResult.successData(200, adrTermCardService.roster(ownLevel == null ? 3 : ownLevel));
    }

    @Log(title = "术语分类卡修改保存", action = "edit")
    @ApiOperation(value = "修改保存", notes = "冻住的卡一句改不得；卡号/版次/层级栏不走这道门")
    @PostMapping("/edit")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult editSave(TAdrTermCard record) {
        return toAjax(adrTermCardService.updateTAdrTermCard(record));
    }

    @Log(title = "术语分类卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "冻住的卡撤不下来")
    @DeleteMapping("/remove")
    @RequiresPermissions("adr:adrTermCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        int n = adrTermCardService.deleteTAdrTermCardByIds(ids);
        return n > 0 ? AjaxResult.success("已撤下" + n + "张") : AjaxResult.error("夹着已冻住的卡或查无，整批不动");
    }
}
