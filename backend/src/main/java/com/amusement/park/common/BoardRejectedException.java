package com.amusement.park.common;

import com.amusement.park.dto.BoardResult;

/**
 * 乘坐核验被拒绝：携带完整的可解释结果（各项核验明细）。
 *
 * <p>与普通 {@link BizException} 一样转成 {ok:false, message, data}，
 * 其中 {@code message} = {@link BoardResult#getReason()}，保证接口拒绝原因
 * 与页面逐项展示的文案一致。</p>
 */
public class BoardRejectedException extends BizException {

    private final transient BoardResult result;

    public BoardRejectedException(BoardResult result) {
        super(result.getReason());
        this.result = result;
    }

    public BoardResult getResult() {
        return result;
    }
}
