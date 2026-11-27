import { unknownVariables, variableNameProblem } from './variables';

describe('variables', () => {
  it('takes names made for braces', () => {
    expect(variableNameProblem('  ')).toBe('empty');
    expect(variableNameProblem('company name')).toBe('invalid');
    expect(variableNameProblem('2fa')).toBe('invalid');
    expect(variableNameProblem(' company_name ')).toBeNull();
  });

  it('finds the names a text uses that the workspace does not have, ignoring case, once each', () => {
    const variables = [{ id: 'v1', name: 'company_name', value: 'Acme' }];
    expect(unknownVariables('For {{ Company_Name }}: {{topics}}, {{TOPICS}}, {{not a name}} and {single}.', variables))
      .toEqual(['topics']);
    expect(unknownVariables('Nothing to fill.', variables)).toEqual([]);
  });
});
