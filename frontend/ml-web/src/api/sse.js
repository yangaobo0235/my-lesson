export function parseSseBlock(block) {
    let id = 0;
    let type = 'message';
    const data = [];
    block.split(/\r?\n/).forEach(line => {
        if (line.startsWith('id:')) id = Number.parseInt(line.slice(3).trim(), 10) || 0;
        if (line.startsWith('event:')) type = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).trim());
    });
    if (!data.length) return null;
    return {id, type, data: JSON.parse(data.join('\n'))};
}
