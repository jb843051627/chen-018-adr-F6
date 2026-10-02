package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.custom.adr.TermCardIntakeVo;
import com.fc.v2.model.custom.adr.TermCardOpenForm;
import com.fc.v2.service.ITAdrTermCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 术语分类卡 Controller。
 * 立卡/改版/三态推进退回/检索/名录——三句硬规矩（分类不重复立卡、一卡不许两挂、
 * 一册不许两家单位共用）与所有判定只听服务层交回的结果，屏上不设第二道门。
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
    @ApiOperation(value = "分页查询", notes = "日常名录只见现行版次")
    @GetMapping("/list")
    @RequiresPermissions("adr:adrTermCard:list")
    @ResponseBody
    public ResultTable list(TAdrTermCard record) {
        QueryWrapper<TAdrTermCard> queryWrapper = new QueryWrapper<TAdrTermCard>();
        startPage();
        com.github.pagehelper.PageInfo<TAdrTermCard> page =
                new com.github.pagehelper.PageInfo<TAdrTermCard>(adrTermCardService.selectTAdrTermCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "术语卡立卡", action = "open")
    @ApiOperation(value = "立卡（表单走法）", notes = "卡号/术语编码系统发、监督层级系统折；缺栏逐栏指名回执；开口晚于收口先问一句")
    @PostMapping("/open")
    @RequiresPermissions("adr:adrTermCard:add")
    @ResponseBody
    public AjaxResult open(@RequestBody TermCardOpenForm form,
                           @RequestParam(value = "confirm", required = false, defaultValue = "false") boolean confirm) {
        TermCardIntakeVo vo = adrTermCardService.insertTAdrTermCard(form, confirm);
        return vo.isAccepted() ? retobject(200, vo) : AjaxResult.error(vo.getMessage()).put("receipt", vo);
    }

    @Log(title = "术语卡改版", action = "revise")
    @ApiOperation(value = "改版", notes = "冻住前可改：旧版挪历史层留旧串，新版版次加一配新串")
    @PostMapping("/revise")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult revise(@RequestParam("id") Long id, @RequestBody TermCardOpenForm form,
                             @RequestParam(value = "confirm", required = false, defaultValue = "false") boolean confirm) {
        TermCardIntakeVo vo = adrTermCardService.revise(id, form, confirm);
        return vo.isAccepted() ? retobject(200, vo) : AjaxResult.error(vo.getMessage()).put("receipt", vo);
    }

    @Log(title = "术语卡推进", action = "advance")
    @ApiOperation(value = "往前迈一步", notes = "待核对→已核对（封校验串）；已核对→已冻住（终局）")
    @PostMapping("/advance")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult advance(@RequestParam("id") Long id) {
        TAdrTermCard card = adrTermCardService.advance(id);
        return card != null ? retobject(200, card) : AjaxResult.error("这一张迈不过去：查无此卡、抢步越级，或已走到冻住");
    }

    @Log(title = "术语卡退回", action = "rollback")
    @ApiOperation(value = "退回一步重做", notes = "冻住前只许已核对退回待核对；旧串保留不覆")
    @PostMapping("/rollback")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult rollback(@RequestParam("id") Long id) {
        TAdrTermCard card = adrTermCardService.rollback(id);
        return card != null ? retobject(200, card) : AjaxResult.error("退不回来：只许从已核对退回待核对，冻住后路不通");
    }

    @ApiOperation(value = "层级点栏", notes = "按分类层级当场点出非写不可的栏")
    @GetMapping("/requiredFields")
    @RequiresPermissions("adr:adrTermCard:view")
    @ResponseBody
    public AjaxResult requiredFields(@RequestParam("termLevel") Integer termLevel) {
        return retobject(200, adrTermCardService.requiredFields(termLevel));
    }

    @Log(title = "分类树检索", action = "search")
    @ApiOperation(value = "分类树检索", notes = "术语名号/症状词/上位分类码三处开搜，命中带上位码与层级；只摆本级现行卡")
    @GetMapping("/search")
    @RequiresPermissions("adr:adrTermCard:view")
    @ResponseBody
    public AjaxResult search(@RequestParam(value = "siteNo", required = false) String siteNo,
                             @RequestParam("keyword") String keyword) {
        return retobject(200, adrTermCardService.search(siteNo, keyword));
    }

    @Log(title = "本级名录占数", action = "roster")
    @ApiOperation(value = "本级名录与占数", notes = "卡与数同一回点算；占数立卡加一、冻住减一")
    @GetMapping("/roster")
    @RequiresPermissions("adr:adrTermCard:list")
    @ResponseBody
    public AjaxResult roster(@RequestParam(value = "siteNo", required = false) String siteNo) {
        return retobject(200, adrTermCardService.roster(siteNo));
    }

    @ApiOperation(value = "卡的各版次", notes = "含历史层旧版，按版次序升序，备事后追溯")
    @GetMapping("/versions")
    @RequiresPermissions("adr:adrTermCard:view")
    @ResponseBody
    public AjaxResult versions(@RequestParam("id") Long id) {
        return retobject(200, adrTermCardService.listVersions(id));
    }

    // ---------------- 旧端点原样留着（同一套规矩，走实体旧签名） ----------------

    @Log(title = "术语分类卡新增", action = "add")
    @ApiOperation(value = "新增(旧入口)", notes = "签名照旧；系统号/系统档不收手填，不合规回收0")
    @PostMapping("/add")
    @RequiresPermissions("adr:adrTermCard:add")
    @ResponseBody
    public AjaxResult add(TAdrTermCard record) {
        return toAjax(adrTermCardService.insertTAdrTermCard(record));
    }

    @Log(title = "术语分类卡修改", action = "edit")
    @ApiOperation(value = "修改保存(旧入口)", notes = "冻住的卡改不动；改份数/换底册当场重折监督层级")
    @PostMapping("/edit")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult editSave(TAdrTermCard record) {
        return toAjax(adrTermCardService.updateTAdrTermCard(record));
    }

    @Log(title = "术语分类卡删除", action = "remove")
    @ApiOperation(value = "删除(旧入口)", notes = "冻住的卡撤不下来；同卡号各版整批沉底留痕")
    @DeleteMapping("/remove")
    @RequiresPermissions("adr:adrTermCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(adrTermCardService.deleteTAdrTermCardByIds(ids));
    }
}
