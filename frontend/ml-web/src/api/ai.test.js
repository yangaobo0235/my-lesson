import {describe, expect, it} from 'vitest';

import {parseSseBlock} from './sse.js';

describe('parseSseBlock', () => {
    it('parses persistent id, named event and JSON data', () => {
        const event = parseSseBlock(
            'id: 42\nevent: answer_delta\ndata: {"delta":"hello"}'
        );
        expect(event).toEqual({
            id: 42,
            type: 'answer_delta',
            data: {delta: 'hello'}
        });
    });

    it('ignores keepalive blocks without data', () => {
        expect(parseSseBlock(': keepalive')).toBeNull();
    });
});
