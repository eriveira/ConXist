import { chatToSend, type ChatLine, type TugaSettings, unchangedTuga } from './tuga';

const person = (text: string): ChatLine => ({ from: 'PERSON', text });
const tuga = (text: string): ChatLine => ({ from: 'TUGA', text });

describe('chatToSend', () => {
  it('sends the whole chat while it is short', () => {
    const chat = [person('Hi'), tuga('Hello'), person('What did we decide?')];
    expect(chatToSend(chat, 40)).toEqual(chat);
  });

  it('keeps the newest messages, starting with one of the person', () => {
    const chat = [person('1'), tuga('2'), person('3'), tuga('4'), person('5')];
    expect(chatToSend(chat, 4)).toEqual([person('3'), tuga('4'), person('5')]);
    expect(chatToSend(chat, 3)).toEqual([person('3'), tuga('4'), person('5')]);
  });

  it('cuts an answer of Tuga to what the API takes back', () => {
    const long = 'a'.repeat(20_005);
    expect(chatToSend([person('Hi'), tuga(long), person('More')], 40)[1].text).toHaveLength(20_000);
  });

  it('sends nothing when no message of the person is left', () => {
    expect(chatToSend([tuga('Hello')], 40)).toEqual([]);
  });
});

describe('unchangedTuga', () => {
  const saved: TugaSettings = {
    revision: 3, ready: true, model: { provider: 'anthropic', model: 'claude-sonnet-5-5', effort: 'MEDIUM' },
    modelPicked: false, prompt: 'ConXist', conxistPrompt: 'ConXist', promptEdited: false, promptChangedAt: null,
    maxPrompt: 4000, maxMessages: 40, maxMessage: 4000,
  };

  it('keeps Tuga on where new agents start, and on ConXist’s prompt, until the workspace picks its own', () => {
    expect(unchangedTuga(saved)).toEqual({ revision: 3, provider: null, model: null, effort: null, prompt: null });
  });

  it('keeps what the workspace picked and wrote', () => {
    expect(unchangedTuga({ ...saved, modelPicked: true, promptEdited: true, prompt: 'Answer in Spanish.' })).toEqual({
      revision: 3, provider: 'anthropic', model: 'claude-sonnet-5-5', effort: 'MEDIUM', prompt: 'Answer in Spanish.',
    });
  });
});
